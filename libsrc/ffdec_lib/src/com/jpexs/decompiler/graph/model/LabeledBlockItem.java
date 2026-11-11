/*
 *  Copyright (C) 2010-2026 JPEXS, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.graph.model;

import com.jpexs.decompiler.flash.SourceGeneratorLocalData;
import com.jpexs.decompiler.flash.helpers.GraphTextWriter;
import com.jpexs.decompiler.graph.Block;
import com.jpexs.decompiler.graph.CompilationException;
import com.jpexs.decompiler.graph.Graph;
import com.jpexs.decompiler.graph.GraphSourceItem;
import com.jpexs.decompiler.graph.GraphTargetDialect;
import com.jpexs.decompiler.graph.GraphTargetItem;
import com.jpexs.decompiler.graph.GraphTargetVisitorInterface;
import com.jpexs.decompiler.graph.SourceGenerator;
import com.jpexs.decompiler.graph.TypeItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Block with a label. A break with the id of the block leaves it, as a
 * break of a loop with a label: loop1: { ... break loop1; ... }. It is not
 * a loop: a break or continue without a label inside it belongs to the
 * loop or switch around.
 *
 * @author JPEXS
 */
public class LabeledBlockItem extends GraphTargetItem implements Block {

    /**
     * Id of the block, from the ids of the loops
     */
    public long id;

    /**
     * Commands
     */
    public List<GraphTargetItem> commands;

    /**
     * Constructor.
     *
     * @param dialect Dialect
     * @param src Source
     * @param lineStartIns Line start instruction
     * @param id Id of the block
     * @param commands Commands
     */
    public LabeledBlockItem(GraphTargetDialect dialect, GraphSourceItem src, GraphSourceItem lineStartIns, long id, List<GraphTargetItem> commands) {
        super(dialect, src, lineStartIns, NOPRECEDENCE);
        this.id = id;
        this.commands = commands;
    }

    @Override
    public void visit(GraphTargetVisitorInterface visitor) {
        visitor.visitAll(commands);
    }

    @Override
    public GraphTextWriter appendTo(GraphTextWriter writer, LocalData localData) throws InterruptedException {
        //The block is not put on the stack of loops of the writer, so every
        //break of it is written with its label
        writer.append("loop").append(id).append(":");
        writer.startBlock();
        Graph.graphToString(commands, writer, localData);
        return writer.endBlock();
    }

    @Override
    public boolean needsSemicolon() {
        return false;
    }

    @Override
    public List<List<GraphTargetItem>> getSubs() {
        List<List<GraphTargetItem>> ret = new ArrayList<>();
        ret.add(commands);
        return ret;
    }

    @Override
    public List<ContinueItem> getContinues() {
        List<ContinueItem> ret = new ArrayList<>();
        for (GraphTargetItem ti : commands) {
            if (ti instanceof ContinueItem) {
                ret.add((ContinueItem) ti);
            }
            if (ti instanceof Block) {
                ret.addAll(((Block) ti).getContinues());
            }
        }
        return ret;
    }

    @Override
    public List<BreakItem> getBreaks() {
        List<BreakItem> ret = new ArrayList<>();
        for (GraphTargetItem ti : commands) {
            if (ti instanceof BreakItem) {
                ret.add((BreakItem) ti);
            }
            if (ti instanceof Block) {
                ret.addAll(((Block) ti).getBreaks());
            }
        }
        return ret;
    }

    @Override
    public List<GraphSourceItem> toSource(SourceGeneratorLocalData localData, SourceGenerator generator) throws CompilationException {
        return generator.generate(localData, commands);
    }

    @Override
    public boolean hasReturnValue() {
        return false;
    }

    @Override
    public GraphTargetItem returnType() {
        return TypeItem.UNBOUNDED;
    }
}
