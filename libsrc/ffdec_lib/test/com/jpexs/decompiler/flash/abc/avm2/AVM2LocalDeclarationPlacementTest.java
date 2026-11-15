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
package com.jpexs.decompiler.flash.abc.avm2;

import com.jpexs.decompiler.flash.abc.avm2.graph.AVM2GraphTargetDialect;
import com.jpexs.decompiler.flash.abc.avm2.model.IntegerValueAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.LocalRegAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NanAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NullAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.StringAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.UndefinedAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.clauses.DeclarationAVM2Item;
import com.jpexs.decompiler.graph.GraphTargetItem;
import com.jpexs.decompiler.graph.Loop;
import com.jpexs.decompiler.graph.TypeItem;
import com.jpexs.decompiler.graph.model.GotoItem;
import com.jpexs.decompiler.graph.model.IfItem;
import com.jpexs.decompiler.graph.model.TrueItem;
import com.jpexs.decompiler.graph.model.WhileItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Placement of declarations of locals on trees which the test SWFs do not
 * have.
 *
 * @author JPEXS
 */
public class AVM2LocalDeclarationPlacementTest {

    private static final int REG = 1;

    private static final int OTHER_REG = 3;

    private static DeclarationAVM2Item prologue() {
        return new DeclarationAVM2Item(new SetLocalAVM2Item(null, null, REG, new NullAVM2Item(null, null), TypeItem.STRING), TypeItem.STRING);
    }

    private static SetLocalAVM2Item assign(String value) {
        return new SetLocalAVM2Item(null, null, REG, new StringAVM2Item(null, null, value), TypeItem.STRING);
    }

    private static SetLocalAVM2Item readIntoOther() {
        return new SetLocalAVM2Item(null, null, OTHER_REG, new LocalRegAVM2Item(null, null, REG, null, TypeItem.STRING), TypeItem.STRING);
    }

    private static SetLocalAVM2Item other() {
        return new SetLocalAVM2Item(null, null, OTHER_REG, new StringAVM2Item(null, null, "x"), TypeItem.STRING);
    }

    private static List<GraphTargetItem> list(GraphTargetItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static TrueItem condition() {
        return new TrueItem(AVM2GraphTargetDialect.INSTANCE, null, null);
    }

    private static WhileItem loop(List<GraphTargetItem> commands) {
        return new WhileItem(AVM2GraphTargetDialect.INSTANCE, null, null, new Loop(0, null, null), list(condition()), commands);
    }

    private static IfItem ifItem(List<GraphTargetItem> onTrue, List<GraphTargetItem> onFalse) {
        return new IfItem(AVM2GraphTargetDialect.INSTANCE, null, null, condition(), onTrue, onFalse);
    }

    @Test
    public void testDirectAssignmentInLoop() {
        WhileItem loop = loop(list(assign("a"), readIntoOther()));
        List<GraphTargetItem> items = list(prologue(), loop);
        new AVM2LocalDeclarationPlacement(1).place(items);

        assertEquals(items.size(), 1, "prologue initialization removed");
        assertSame(items.get(0), loop);
        assertTrue(loop.commands.get(0) instanceof DeclarationAVM2Item);
        DeclarationAVM2Item declaration = (DeclarationAVM2Item) loop.commands.get(0);
        assertTrue(declaration.showValue);
        assertEquals(((StringAVM2Item) declaration.assignment.value).getValue(), "a");
    }

    @Test
    public void testAssignedInBranchesOfLoopStaysOutOfLoop() {
        //while(true) { if(..) a = "a"; else a = "b"; b = a; }
        WhileItem loop = loop(list(ifItem(list(assign("a")), list(assign("b"))), readIntoOther()));
        List<GraphTargetItem> items = list(prologue(), other(), loop);
        new AVM2LocalDeclarationPlacement(1).place(items);

        assertEquals(items.size(), 3);
        assertTrue(items.get(0) instanceof SetLocalAVM2Item);
        assertTrue(items.get(1) instanceof DeclarationAVM2Item, "declared just before the loop, not inside it");
        DeclarationAVM2Item declaration = (DeclarationAVM2Item) items.get(1);
        assertTrue(declaration.assignment.value instanceof NullAVM2Item, "keeps the initial value");
        assertSame(items.get(2), loop);
        assertFalse(loop.commands.get(0) instanceof DeclarationAVM2Item);
    }

    @Test
    public void testElseIfChainIsKept() {
        //if(..) b = "x"; else if(..) a = "a"; else a = "b";
        IfItem inner = ifItem(list(assign("a")), list(assign("b")));
        IfItem outer = ifItem(list(other()), list(inner));
        List<GraphTargetItem> items = list(prologue(), other(), outer);
        new AVM2LocalDeclarationPlacement(1).place(items);

        assertEquals(items.size(), 3);
        assertTrue(items.get(1) instanceof DeclarationAVM2Item, "declared before the whole chain");
        assertSame(items.get(2), outer);
        assertEquals(outer.onFalse.size(), 1);
        assertSame(outer.onFalse.get(0), inner);
    }

    @Test
    public void testUnstructuredCodeIsNotChanged() {
        DeclarationAVM2Item declaration = prologue();
        List<GraphTargetItem> items = list(declaration, new GotoItem(AVM2GraphTargetDialect.INSTANCE, null, null, "label1"), assign("a"));
        new AVM2LocalDeclarationPlacement(1).place(items);

        assertEquals(items.size(), 3);
        assertSame(items.get(0), declaration);
    }

    @Test
    public void testParametersAreNotMoved() {
        //register 1 is a parameter here
        DeclarationAVM2Item declaration = prologue();
        List<GraphTargetItem> items = list(declaration, other(), assign("a"));
        new AVM2LocalDeclarationPlacement(2).place(items);

        assertSame(items.get(0), declaration);
    }

    @Test
    public void testDefaultValues() {
        assertTrue(AVM2LocalDeclarationPlacement.isDefaultValue(new IntegerValueAVM2Item(null, null, 0), TypeItem.INT));
        assertTrue(AVM2LocalDeclarationPlacement.isDefaultValue(new IntegerValueAVM2Item(null, null, 0), TypeItem.UINT));
        assertFalse(AVM2LocalDeclarationPlacement.isDefaultValue(new IntegerValueAVM2Item(null, null, 0), TypeItem.NUMBER), "Number starts with NaN");
        assertTrue(AVM2LocalDeclarationPlacement.isDefaultValue(new NanAVM2Item(null, null), TypeItem.NUMBER));
        assertTrue(AVM2LocalDeclarationPlacement.isDefaultValue(new NullAVM2Item(null, null), TypeItem.STRING));
        assertFalse(AVM2LocalDeclarationPlacement.isDefaultValue(new NullAVM2Item(null, null), TypeItem.UNBOUNDED), "* starts with undefined");
        assertTrue(AVM2LocalDeclarationPlacement.isDefaultValue(new UndefinedAVM2Item(null, null), TypeItem.UNBOUNDED));
        assertFalse(AVM2LocalDeclarationPlacement.isDefaultValue(new IntegerValueAVM2Item(null, null, 1), TypeItem.INT));
    }
}
