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
package com.jpexs.decompiler.graph;

import com.jpexs.decompiler.graph.model.BreakItem;
import com.jpexs.decompiler.graph.model.ContinueItem;
import com.jpexs.decompiler.graph.model.ExitItem;
import com.jpexs.decompiler.graph.model.ForItem;
import com.jpexs.decompiler.graph.model.GotoItem;
import com.jpexs.decompiler.graph.model.IfItem;
import com.jpexs.decompiler.graph.model.LabelItem;
import com.jpexs.decompiler.graph.model.LabeledBlockItem;
import com.jpexs.decompiler.graph.model.LoopItem;
import com.jpexs.decompiler.graph.model.SwitchItem;
import com.jpexs.decompiler.graph.model.TrueItem;
import com.jpexs.decompiler.graph.model.UniversalLoopItem;
import com.jpexs.decompiler.graph.model.WhileItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Writes the gotos left in the decompiled code as breaks of blocks with a
 * label and as loops, where the control flow allows it.
 * <p>
 * A goto is left when its target was already written somewhere else. The
 * rules, applied until nothing changes:
 * <ul>
 * <li>goto forward to a label in the same list of commands: the commands
 * between them go to a block with a label, the goto is a break of it</li>
 * <li>goto backward to a label in the same list: the commands between them
 * go to a loop, the goto is a continue of it</li>
 * <li>label inside a branch of an if: the code from the label to the end of
 * the branch is moved after the if (the branch leaves a block around the if
 * to reach it, the other branch leaves a block around both to skip it)</li>
 * <li>label inside a loop or a case of a switch: the same, when the code from
 * the label never continues the loop nor falls to the next case</li>
 * <li>goto before a block which has the label: the commands from the goto to
 * the block go into the block</li>
 * <li>goto after a block which has the label: the code of the label is moved
 * after the goto, when it never continues with the code after it</li>
 * </ul>
 * Code which is moved and reaches the end of its list gets the jump which
 * does what that end does: the break of the block whose commands the list
 * are, or the continue of the loop whose body it is.
 * The label inside a try or a catch seen from outside, or inside a loop which
 * its code continues (a loop with two entries), stays as goto.
 *
 * @author JPEXS
 */
public class GotoEliminator {

    private final GraphTargetDialect dialect;

    private long nextId;

    private static class Step {

        List<GraphTargetItem> list;
        int index;

        Step(List<GraphTargetItem> list, int index) {
            this.list = list;
            this.index = index;
        }

        GraphTargetItem item() {
            return list.get(index);
        }
    }

    /**
     * Constructor.
     *
     * @param dialect Dialect
     */
    public GotoEliminator(GraphTargetDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * Rewrites the gotos of the commands.
     *
     * @param root Commands
     */
    public void eliminate(List<GraphTargetItem> root) {
        if (collectGotos(root).isEmpty()) {
            return;
        }
        inlineTargetCommands(root);
        nextId = maxId(root) + 1;

        boolean changedInRound = true;
        int limit = 1000000;
        while (changedInRound && limit > 0) {
            changedInRound = false;
            Set<GotoItem> failed = Collections.newSetFromMap(new IdentityHashMap<>());
            boolean changed = true;
            while (changed && limit > 0) {
                changed = false;
                for (GotoItem g : collectGotos(root)) {
                    if (failed.contains(g)) {
                        continue;
                    }
                    limit--;
                    if (tryEliminate(root, g)) {
                        changed = true;
                        changedInRound = true;
                        break;
                    }
                    failed.add(g);
                }
            }
        }
        String before;
        int rounds = 0;
        do {
            before = signature(root);
            removeUnusedLabels(root);
            cleanup(root);
            rounds++;
        } while (rounds < 50 && !before.equals(signature(root)));
    }

    private boolean tryEliminate(List<GraphTargetItem> root, GotoItem g) {
        LabelItem label = findLabel(root, g.labelName);
        if (label == null) {
            return false;
        }
        List<Step> pg = new ArrayList<>();
        List<Step> pl = new ArrayList<>();
        if (!findPath(root, g, pg) || !findPath(root, label, pl)) {
            return false;
        }
        int c = 0;
        while (c + 1 < pg.size() && c + 1 < pl.size()
                && pg.get(c).index == pl.get(c).index
                && pg.get(c + 1).list == pl.get(c + 1).list) {
            c++;
        }
        List<GraphTargetItem> common = pg.get(c).list;
        int i = pg.get(c).index;
        int j = pl.get(c).index;
        String labelName = g.labelName;
        GraphTargetItem endJump = endJump(pg, c);

        if (pl.size() == c + 1) {
            //the label is in the common list
            if (i < j) {
                if (pg.size() == c + 1 && i == j - 1) {
                    //goto to the next command
                    common.remove(i);
                    return true;
                }
                long id = nextId++;
                List<GraphTargetItem> body = slice(common, i, j);
                replaceGotos(body, labelName, id, false);
                removeRange(common, i, j);
                common.add(i, new LabeledBlockItem(dialect, null, null, id, body));
                return true;
            }
            if (j < i) {
                long id = nextId++;
                List<GraphTargetItem> body = slice(common, j + 1, i + 1);
                replaceGotos(body, labelName, id, true);
                if (canCompleteNormally(body)) {
                    body.add(new BreakItem(dialect, null, null, id));
                }
                removeRange(common, j + 1, i + 1);
                common.add(j + 1, new UniversalLoopItem(dialect, null, null, new Loop(id, null, null), body));
                return true;
            }
            return false;
        }

        //the label is deeper: move it out of the innermost command which is not a block
        for (int t = pl.size() - 2; t >= c; t--) {
            GraphTargetItem container = pl.get(t).item();
            if (container instanceof LabeledBlockItem) {
                continue;
            }
            Step labelStep = pl.get(pl.size() - 1);
            if (lift(pl.get(t).list, pl.get(t).index, container, pl.get(t + 1).list, pl.get(t + 1).index, labelStep.list, labelStep.index, pl, t + 1)) {
                return true;
            }
            //The goto after a loop without end, back to its body: the code
            //from the loop to the goto belongs to the loop
            if (t == c && i > j && moveCodeToOnlyBreak(common, j, i, endJump)) {
                return true;
            }
            if (loopWithoutEndToBlock(pl.get(t).list, pl.get(t).index)) {
                return true;
            }
            if (i <= j) {
                return false;
            }
            if (forToLoopWithoutEnd(pl.get(t).list, pl.get(t).index)) {
                return true;
            }
            if (t == c) {
                return moveCodeIntoLoop(common, j, i, container, -1, endJump);
            }
            //the loop is in blocks only: the code after the outermost block,
            //reached only from the loop, is part of the loop
            for (int u = c; u < t; u++) {
                if (!(pl.get(u).item() instanceof LabeledBlockItem)) {
                    return false;
                }
            }
            return moveCodeToOnlyBreak(common, j, i, endJump) || moveCodeIntoLoop(common, j, i, container, ((LabeledBlockItem) common.get(j)).id, endJump);
        }

        //the label is in blocks only
        LabeledBlockItem outerBlock = (LabeledBlockItem) common.get(j);
        if (i < j) {
            List<GraphTargetItem> moved = slice(common, i, j);
            removeRange(common, i, j);
            insertAll(outerBlock.commands, 0, moved);
            return true;
        }
        if (i > j) {
            Step last = pl.get(pl.size() - 1);
            List<GraphTargetItem> labelList = last.list;
            List<GraphTargetItem> tail = slice(labelList, last.index, labelList.size());
            Set<Long> blockIds = new HashSet<>();
            for (int t = c; t < pl.size() - 1; t++) {
                blockIds.add(((LabeledBlockItem) pl.get(t).item()).id);
            }
            //The code of the label may end at the end of a block it is in, or
            //leave one of them, when a simple jump follows the block: the jump
            //is written in its place
            if (!resolveJumpsAfterBlocks(tail, pl, c, -1)) {
                return moveCodeToOnlyBreak(common, j, i, endJump) || moveLabelWithCodeAfterBlocks(common, j, i, pl, c, labelName);
            }
            long targetId = nextId++;
            removeRange(labelList, last.index, labelList.size());
            if (canCompleteNormally(labelList)) {
                labelList.add(new BreakItem(dialect, null, null, targetId));
            }
            List<GraphTargetItem> segment = slice(common, j, i + 1);
            replaceGotos(segment, labelName, targetId, false);
            removeRange(common, j, i + 1);
            if (canCompleteNormally(segment)) {
                long outId = nextId++;
                segment.add(new BreakItem(dialect, null, null, outId));
                List<GraphTargetItem> outCommands = new ArrayList<>();
                outCommands.add(new LabeledBlockItem(dialect, null, null, targetId, segment));
                outCommands.addAll(tail);
                common.add(j, new LabeledBlockItem(dialect, null, null, outId, outCommands));
            } else {
                common.add(j, new LabeledBlockItem(dialect, null, null, targetId, segment));
                insertAll(common, j + 1, tail);
            }
            return true;
        }
        return false;
    }

    /**
     * Shape of the commands, to know whether the cleanup changed them.
     */
    private String signature(List<GraphTargetItem> list) {
        StringBuilder sb = new StringBuilder();
        signature(list, sb);
        return sb.toString();
    }

    private void signature(List<GraphTargetItem> list, StringBuilder sb) {
        if (list == null) {
            return;
        }
        sb.append('[');
        for (GraphTargetItem item : list) {
            if (item instanceof BreakItem) {
                sb.append('b').append(((BreakItem) item).loopId);
            } else if (item instanceof ContinueItem) {
                sb.append('c').append(((ContinueItem) item).loopId);
            } else if (item instanceof LabeledBlockItem) {
                sb.append('l').append(((LabeledBlockItem) item).id);
            } else if (item instanceof LabelItem) {
                sb.append('a');
            } else {
                sb.append('i');
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    signature(sub, sb);
                }
            }
        }
        sb.append(']');
    }

    /**
     * Simplifies what the rules leave behind, until nothing changes:
     * <ul>
     * <li>a break of a block which is followed by a break or a continue is
     * that jump</li>
     * <li>a break of a block as its last command is not needed</li>
     * <li>a block which no break leaves is its commands</li>
     * <li>the commands right after a jump are removed (nothing reaches
     * them)</li>
     * </ul>
     */
    private void cleanup(List<GraphTargetItem> list) {
        if (list == null) {
            return;
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int n = 0; n < list.size(); n++) {
                GraphTargetItem item = list.get(n);
                if (item instanceof LabeledBlockItem) {
                    LabeledBlockItem block = (LabeledBlockItem) item;
                    Set<Long> ids = new HashSet<>();
                    ids.add(block.id);
                    if (n + 1 < list.size() && hasJumpTo(block.commands, ids, true)) {
                        GraphTargetItem next = list.get(n + 1);
                        if ((next instanceof BreakItem) || (next instanceof ContinueItem)) {
                            replaceBreaks(block.commands, block.id, next);
                            changed = true;
                        }
                    }
                    if (!block.commands.isEmpty()) {
                        GraphTargetItem last = block.commands.get(block.commands.size() - 1);
                        if ((last instanceof BreakItem) && ((BreakItem) last).loopId == block.id) {
                            block.commands.remove(block.commands.size() - 1);
                            changed = true;
                        }
                    }
                    if (!hasJumpTo(block.commands, ids, true)) {
                        list.remove(n);
                        insertAll(list, n, block.commands);
                        n--;
                        changed = true;
                        continue;
                    }
                }
                boolean jump = (item instanceof BreakItem) || (item instanceof ContinueItem) || (item instanceof ExitItem)
                        || ((item instanceof GotoItem) && ((GotoItem) item).targetCommands == null);
                if (jump) {
                    while (n + 1 < list.size() && !(list.get(n + 1) instanceof LabelItem)) {
                        list.remove(n + 1);
                        changed = true;
                    }
                }
            }
        }
        for (int n = 0; n < list.size(); n++) {
            GraphTargetItem item = list.get(n);
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    cleanup(sub);
                }
            }
        }
    }

    /**
     * Replaces the breaks of the id with a copy of the jump.
     */
    private void replaceBreaks(List<GraphTargetItem> list, long id, GraphTargetItem jump) {
        if (list == null) {
            return;
        }
        for (int n = 0; n < list.size(); n++) {
            GraphTargetItem item = list.get(n);
            if ((item instanceof BreakItem) && ((BreakItem) item).loopId == id) {
                list.set(n, copyJump(jump));
                continue;
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    replaceBreaks(sub, id, jump);
                }
            }
        }
    }

    /**
     * The code after a loop, which ends with a goto back into the loop, is
     * part of the loop: the body goes to a block, the breaks of the loop
     * leave the block, and the code follows it inside the loop.
     * <pre>
     * while(true) { a; label: b; } x; goto label;
     * </pre> becomes
     * <pre>
     * while(true) { n: { a; label: b; continue; } x; goto label; }
     * </pre> A while with a condition is first written as a while(true) which
     * breaks when the condition is false.
     *
     * The loop may also be inside the block before the code (in blocks
     * only), when every break of that block is in the loop: these breaks are
     * the ones which leave the new block.
     *
     * @param list List with the loop, or with the block which has the loop
     * @param index Index of the loop or of the block
     * @param lastIndex Index of the last command of the code after it
     * @param item The loop
     * @param blockId Id of the block which has the loop, -1 when the loop is
     * in the list
     * @param endJump The jump which does what the end of the list does (see
     * {@link #endJump}), or null
     * @return True if moved
     */
    private boolean moveCodeIntoLoop(List<GraphTargetItem> list, int index, int lastIndex, GraphTargetItem item, long blockId, GraphTargetItem endJump) {
        if (item.getClass() != WhileItem.class && item.getClass() != UniversalLoopItem.class) {
            return false;
        }
        WhileItem loop = (WhileItem) item;
        if (loop.loop == null || loop.commands == null) {
            return false;
        }
        int[] last = new int[1];
        List<GraphTargetItem> code = codeAfter(list, index, lastIndex, endJump, last);
        if (code == null) {
            return false;
        }
        lastIndex = last[0];
        long loopId = loop.loop.id;
        List<GraphTargetItem> body = new ArrayList<>();
        long capturedId = loopId;
        if (blockId >= 0) {
            //every break of the block must be in the loop
            List<Step> all = new ArrayList<>();
            List<Step> inLoop = new ArrayList<>();
            findBreakSites(((LabeledBlockItem) list.get(index)).commands, blockId, all);
            findBreakSites(loop.commands, blockId, inLoop);
            if (inLoop.isEmpty() || all.size() != inLoop.size()) {
                return false;
            }
            capturedId = blockId;
        } else if (!isLoopWithoutEnd(loop)) {
            if (loop.expression == null || loop.expression.size() != 1) {
                return false;
            }
            List<GraphTargetItem> onFalse = new ArrayList<>();
            onFalse.add(new BreakItem(dialect, null, null, loopId));
            body.add(new IfItem(dialect, null, null, loop.expression.get(0), new ArrayList<>(), onFalse));
            loop = new UniversalLoopItem(dialect, null, null, loop.loop, loop.commands);
            list.set(index, loop);
        }
        body.addAll(loop.commands);
        if (canCompleteNormally(body)) {
            body.add(new ContinueItem(dialect, null, null, loopId));
        }
        long newBlockId = nextId++;
        retargetBreaks(body, capturedId, newBlockId);
        removeRange(list, index + 1, lastIndex + 1);
        List<GraphTargetItem> commands = new ArrayList<>();
        commands.add(new LabeledBlockItem(dialect, null, null, newBlockId, body));
        commands.addAll(code);
        loop.commands = commands;
        return true;
    }

    /**
     * A for is written as a while(true), so that the code after it can be
     * moved into it:
     * <pre>
     * for(a; b; c) { d; }
     * </pre> becomes
     * <pre>
     * a; while(true) { if(!b) break; n: { d; } c; }
     * </pre> where a continue of the for in d is a break of n.
     *
     * @param list List with the for
     * @param index Index of the for
     * @return True if changed
     */
    private boolean forToLoopWithoutEnd(List<GraphTargetItem> list, int index) {
        GraphTargetItem item = list.get(index);
        if (item.getClass() != ForItem.class) {
            return false;
        }
        ForItem forItem = (ForItem) item;
        if (forItem.loop == null || forItem.commands == null) {
            return false;
        }
        long loopId = forItem.loop.id;
        List<GraphTargetItem> body = new ArrayList<>();
        if (forItem.expression != null && !(forItem.expression instanceof TrueItem)) {
            List<GraphTargetItem> onFalse = new ArrayList<>();
            onFalse.add(new BreakItem(dialect, null, null, loopId));
            body.add(new IfItem(dialect, null, null, forItem.expression, new ArrayList<>(), onFalse));
        }
        long blockId = nextId++;
        List<GraphTargetItem> inner = new ArrayList<>(forItem.commands);
        replaceContinues(inner, loopId, blockId);
        body.add(new LabeledBlockItem(dialect, null, null, blockId, inner));
        if (forItem.finalCommands != null) {
            body.addAll(forItem.finalCommands);
        }
        list.set(index, new UniversalLoopItem(dialect, null, null, forItem.loop, body));
        if (forItem.firstCommands != null) {
            insertAll(list, index, new ArrayList<>(forItem.firstCommands));
        }
        return true;
    }

    private void replaceContinues(List<GraphTargetItem> list, long loopId, long blockId) {
        if (list == null) {
            return;
        }
        for (int n = 0; n < list.size(); n++) {
            GraphTargetItem item = list.get(n);
            if ((item instanceof ContinueItem) && ((ContinueItem) item).loopId == loopId) {
                list.set(n, new BreakItem(dialect, null, null, blockId));
                continue;
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    replaceContinues(sub, loopId, blockId);
                }
            }
        }
    }

    /**
     * A while(true) which never repeats (its body does not reach its end and
     * has no continue of it) is a block: the code after it can be moved.
     *
     * @param list List with the loop
     * @param index Index of the loop
     * @return True if changed
     */
    private boolean loopWithoutEndToBlock(List<GraphTargetItem> list, int index) {
        GraphTargetItem item = list.get(index);
        if (!isLoopWithoutEnd(item)) {
            return false;
        }
        WhileItem loop = (WhileItem) item;
        Set<Long> ids = new HashSet<>();
        ids.add(loop.loop.id);
        if (canCompleteNormally(loop.commands) || hasJumpTo(loop.commands, ids, false)) {
            return false;
        }
        list.set(index, new LabeledBlockItem(dialect, null, null, loop.loop.id, loop.commands));
        return true;
    }

    /**
     * The goto is after a block, the label is inside it, in blocks only. The
     * code of the label, and the code which follows each block it is in, go
     * after the goto:
     * <pre>
     * b1: { a; b2: { p; label: t; } r; } x; goto label;
     * </pre> becomes
     * <pre>
     * s2: { n: { b1: { a; b2: { p; break n; } } x; break n; } t; } r;
     * </pre> where a break of b2 is a break of s2. The code after the
     * outermost block (x) is reached as before, by the breaks of b1.
     *
     * @return True if moved
     */
    private boolean moveLabelWithCodeAfterBlocks(List<GraphTargetItem> common, int j, int i, List<Step> pl, int c, String labelName) {
        int last = pl.size() - 1;
        Step labelStep = pl.get(last);
        LabeledBlockItem outerBlock = (LabeledBlockItem) pl.get(c).item();
        List<GraphTargetItem> tail = slice(labelStep.list, labelStep.index, labelStep.list.size());
        //rests.get(m): the code after the block of the step m (m from c + 1)
        java.util.Map<Integer, List<GraphTargetItem>> rests = new java.util.HashMap<>();
        Set<Long> outerId = new HashSet<>();
        outerId.add(outerBlock.id);
        if (hasJumpTo(tail, outerId, true)) {
            return false;
        }
        boolean completes = canCompleteNormally(tail);
        for (int m = last - 1; m > c; m--) {
            Step step = pl.get(m);
            List<GraphTargetItem> rest = slice(step.list, step.index + 1, step.list.size());
            if (hasJumpTo(rest, outerId, true)) {
                return false;
            }
            rests.put(m, rest);
            Set<Long> id = new HashSet<>();
            id.add(((LabeledBlockItem) step.item()).id);
            boolean reached = completes || hasJumpTo(outerBlock.commands, id, true);
            completes = reached && canCompleteNormally(rest);
        }
        if (completes) {
            //the code would continue with the code after the outermost block: a loop
            return false;
        }

        long targetId = nextId++;
        removeRange(labelStep.list, labelStep.index, labelStep.list.size());
        if (canCompleteNormally(labelStep.list)) {
            labelStep.list.add(new BreakItem(dialect, null, null, targetId));
        }
        java.util.Map<Integer, Long> wrapperIds = new java.util.HashMap<>();
        for (int m = last - 1; m > c; m--) {
            Step step = pl.get(m);
            removeRange(step.list, step.index + 1, step.list.size());
            long wrapperId = nextId++;
            wrapperIds.put(m, wrapperId);
            long blockId = ((LabeledBlockItem) step.item()).id;
            retargetBreaks(outerBlock.commands, blockId, wrapperId);
            retargetBreaks(tail, blockId, wrapperId);
            for (List<GraphTargetItem> rest : rests.values()) {
                retargetBreaks(rest, blockId, wrapperId);
            }
        }
        List<GraphTargetItem> segment = slice(common, j, i + 1);
        replaceGotos(segment, labelName, targetId, false);
        removeRange(common, j, i + 1);
        long outId = -1;
        if (canCompleteNormally(segment)) {
            outId = nextId++;
            segment.add(new BreakItem(dialect, null, null, outId));
        }
        List<GraphTargetItem> current = new ArrayList<>();
        current.add(new LabeledBlockItem(dialect, null, null, targetId, segment));
        current.addAll(tail);
        for (int m = last - 1; m > c; m--) {
            List<GraphTargetItem> next = new ArrayList<>();
            next.add(new LabeledBlockItem(dialect, null, null, wrapperIds.get(m), current));
            next.addAll(rests.get(m));
            current = next;
        }
        if (outId >= 0) {
            common.add(j, new LabeledBlockItem(dialect, null, null, outId, current));
        } else {
            insertAll(common, j, current);
        }
        return true;
    }

    /**
     * Makes the commands independent of the blocks they are in (the items of
     * the path from the index on, all blocks): the end of the commands and
     * the breaks of these blocks become the jump which follows the block.
     *
     * @param commands Commands, at the end of the innermost block
     * @param path Path to the commands
     * @param first Index of the outermost block in the path
     * @param loopId Id of a loop which the jumps must not continue, or -1
     * @return False when it is not possible, the commands are then unchanged
     */
    private boolean resolveJumpsAfterBlocks(List<GraphTargetItem> commands, List<Step> path, int first, long loopId) {
        if (first > path.size() - 2) {
            //no block
            return !canCompleteNormally(commands);
        }
        java.util.Map<Long, GraphTargetItem> after = new java.util.HashMap<>();
        for (int m = first; m < path.size() - 1; m++) {
            LabeledBlockItem block = (LabeledBlockItem) path.get(m).item();
            after.put(block.id, jumpAfterBlock(path, first, m, 0));
        }
        List<Step> sites = new ArrayList<>();
        for (Long id : after.keySet()) {
            List<Step> found = new ArrayList<>();
            findBreakSites(commands, id, found);
            if (!found.isEmpty() && !isJumpAllowed(after.get(id), loopId)) {
                return false;
            }
            sites.addAll(found);
        }
        GraphTargetItem atEnd = null;
        if (canCompleteNormally(commands)) {
            atEnd = after.get(((LabeledBlockItem) path.get(path.size() - 2).item()).id);
            if (!isJumpAllowed(atEnd, loopId)) {
                return false;
            }
        }
        for (Step site : sites) {
            site.list.set(site.index, copyJump(after.get(((BreakItem) site.item()).loopId)));
        }
        if (atEnd != null) {
            commands.add(copyJump(atEnd));
        }
        return true;
    }

    /**
     * Gets the simple jump which is executed after a block of the path: the
     * break or continue right after it, or after the blocks which it ends.
     */
    private GraphTargetItem jumpAfterBlock(List<Step> path, int first, int m, int depth) {
        if (depth > path.size()) {
            return null;
        }
        Step step = path.get(m);
        if (step.index + 1 >= step.list.size()) {
            return m > first ? jumpAfterBlock(path, first, m - 1, depth + 1) : null;
        }
        GraphTargetItem next = step.list.get(step.index + 1);
        if (next instanceof ContinueItem) {
            return next;
        }
        if (next instanceof BreakItem) {
            long id = ((BreakItem) next).loopId;
            for (int k = first; k < m; k++) {
                if (((LabeledBlockItem) path.get(k).item()).id == id) {
                    return jumpAfterBlock(path, first, k, depth + 1);
                }
            }
            return next;
        }
        return null;
    }

    private boolean isJumpAllowed(GraphTargetItem jump, long loopId) {
        if (jump == null) {
            return false;
        }
        return !((jump instanceof ContinueItem) && ((ContinueItem) jump).loopId == loopId);
    }

    private GraphTargetItem copyJump(GraphTargetItem jump) {
        if (jump instanceof ContinueItem) {
            return new ContinueItem(dialect, null, null, ((ContinueItem) jump).loopId);
        }
        return new BreakItem(dialect, null, null, ((BreakItem) jump).loopId);
    }

    /**
     * The code after a block (or after a loop without end) which is reached
     * only by one break of it goes to the place of the break: the goto in it
     * is then before the label, or inside the loop.
     *
     * @param list List with the block
     * @param blockIndex Index of the block
     * @param lastIndex Index of the last command of the code after the block
     * @param endJump The jump which does what the end of the list does (see
     * {@link #endJump}), or null
     * @return True if moved
     */
    private boolean moveCodeToOnlyBreak(List<GraphTargetItem> list, int blockIndex, int lastIndex, GraphTargetItem endJump) {
        GraphTargetItem item = list.get(blockIndex);
        List<GraphTargetItem> commands;
        long id;
        if (item instanceof LabeledBlockItem) {
            commands = ((LabeledBlockItem) item).commands;
            id = ((LabeledBlockItem) item).id;
            if (canCompleteNormally(commands)) {
                return false;
            }
        } else if (isLoopWithoutEnd(item)) {
            commands = ((WhileItem) item).commands;
            id = ((WhileItem) item).loop.id;
        } else {
            return false;
        }
        List<Step> sites = new ArrayList<>();
        if (!findBreakSites(commands, id, sites) || sites.size() != 1) {
            return false;
        }
        int[] last = new int[1];
        List<GraphTargetItem> code = codeAfter(list, blockIndex, lastIndex, endJump, last);
        if (code == null) {
            return false;
        }
        lastIndex = last[0];
        removeRange(list, blockIndex + 1, lastIndex + 1);
        Step site = sites.get(0);
        site.list.remove(site.index);
        insertAll(site.list, site.index, code);
        return true;
    }

    /**
     * The code after a command, which is moved somewhere else: the commands up
     * to the one with the goto, or, when these continue, all the commands to
     * the end of the list. Code which reaches the end of the list gets the
     * jump which does what the end does, so that it does not depend on its
     * place any more.
     *
     * @param list List with the command
     * @param index Index of the command
     * @param lastIndex Index of the command with the goto
     * @param endJump The jump which does what the end of the list does (see
     * {@link #endJump}), or null
     * @param last Gets the index of the last command of the code in the list
     * @return The code, or null when it reaches the end of the list and there
     * is no such jump
     */
    private List<GraphTargetItem> codeAfter(List<GraphTargetItem> list, int index, int lastIndex, GraphTargetItem endJump, int[] last) {
        List<GraphTargetItem> code = slice(list, index + 1, lastIndex + 1);
        last[0] = lastIndex;
        if (!canCompleteNormally(code)) {
            return code;
        }
        //the code up to the goto continues: all the code to the end of the list
        last[0] = list.size() - 1;
        code = slice(list, index + 1, list.size());
        if (canCompleteNormally(code)) {
            if (endJump == null) {
                return null;
            }
            code.add(copyJump(endJump));
        }
        return code;
    }

    /**
     * Gets the jump which does what the end of the list of the path step
     * does: the break of the block whose commands it is, or the continue of
     * the loop whose body it is.
     *
     * @param path Path
     * @param step Index of the step in the path
     * @return The jump, or null when the end of the list does something else
     */
    private GraphTargetItem endJump(List<Step> path, int step) {
        if (step < 1) {
            return null;
        }
        List<GraphTargetItem> list = path.get(step).list;
        GraphTargetItem container = path.get(step - 1).item();
        if ((container instanceof LabeledBlockItem) && ((LabeledBlockItem) container).commands == list) {
            return new BreakItem(dialect, null, null, ((LabeledBlockItem) container).id);
        }
        if ((container instanceof LoopItem) && !(container instanceof SwitchItem)) {
            LoopItem loopItem = (LoopItem) container;
            if (loopItem.loop != null && loopItem.hasBaseBody() && loopItem.getBaseBodyCommands() == list) {
                return new ContinueItem(dialect, null, null, loopItem.loop.id);
            }
        }
        return null;
    }

    /**
     * Finds the breaks of the id.
     *
     * @return False when one of them is inside a command which the code can
     * not be moved into (a try, for example)
     */
    private boolean findBreakSites(List<GraphTargetItem> list, long id, List<Step> sites) {
        if (list == null) {
            return true;
        }
        for (int n = 0; n < list.size(); n++) {
            GraphTargetItem item = list.get(n);
            if ((item instanceof BreakItem) && ((BreakItem) item).loopId == id) {
                sites.add(new Step(list, n));
            }
            if (item instanceof Block) {
                boolean movable = (item instanceof IfItem) || (item instanceof LabeledBlockItem) || (item instanceof LoopItem);
                List<Step> inner = new ArrayList<>();
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    if (!findBreakSites(sub, id, inner)) {
                        return false;
                    }
                }
                if (!inner.isEmpty() && !movable) {
                    return false;
                }
                sites.addAll(inner);
            }
        }
        return true;
    }

    /**
     * Moves the commands of a sub list of a command, from an index to the
     * end, after the command.
     *
     * @param parentList List with the command
     * @param parentIndex Index of the command in the list
     * @param container The command
     * @param sub Sub list of the command
     * @param index Index of the first moved command in the sub list
     * @param labelList List with the label (the sub list, or the commands of
     * a block in it)
     * @param labelIndex Index of the label in its list
     * @param path Path to the label
     * @param firstBlock Index in the path of the first block between the sub
     * list and the label
     * @return True if moved
     */
    private boolean lift(List<GraphTargetItem> parentList, int parentIndex, GraphTargetItem container, List<GraphTargetItem> sub, int index, List<GraphTargetItem> labelList, int labelIndex, List<Step> path, int firstBlock) {
        List<GraphTargetItem> lifted = slice(sub, index, sub.size());

        if (container instanceof IfItem) {
            IfItem ifItem = (IfItem) container;
            List<GraphTargetItem> other;
            if (sub == ifItem.onTrue) {
                other = ifItem.onFalse;
            } else if (sub == ifItem.onFalse) {
                other = ifItem.onTrue;
            } else {
                return false;
            }
            long innerId = nextId++;
            removeRange(sub, index, sub.size());
            if (canCompleteNormally(sub)) {
                sub.add(new BreakItem(dialect, null, null, innerId));
            }
            List<GraphTargetItem> innerCommands = new ArrayList<>();
            innerCommands.add(container);
            LabeledBlockItem inner = new LabeledBlockItem(dialect, null, null, innerId, innerCommands);
            if (other == null || canCompleteNormally(other)) {
                long outerId = nextId++;
                innerCommands.add(new BreakItem(dialect, null, null, outerId));
                List<GraphTargetItem> outerCommands = new ArrayList<>();
                outerCommands.add(inner);
                outerCommands.addAll(lifted);
                parentList.set(parentIndex, new LabeledBlockItem(dialect, null, null, outerId, outerCommands));
            } else {
                parentList.set(parentIndex, inner);
                insertAll(parentList, parentIndex + 1, lifted);
            }
            return true;
        }

        if (container instanceof LoopItem) {
            LoopItem loopItem = (LoopItem) container;
            if (loopItem.loop == null) {
                return false;
            }
            long loopId = loopItem.loop.id;
            boolean isSwitch = container instanceof SwitchItem;
            if (isSwitch) {
                boolean isCase = false;
                for (List<GraphTargetItem> caseCommands : ((SwitchItem) container).caseCommands) {
                    if (caseCommands == sub) {
                        isCase = true;
                    }
                }
                if (!isCase) {
                    return false;
                }
            } else if (!loopItem.hasBaseBody() || loopItem.getBaseBodyCommands() != sub) {
                return false;
            }
            Set<Long> ids = new HashSet<>();
            ids.add(loopId);
            //Only the code from the label to the end of its list leaves the loop.
            //It must not continue the loop nor fall to the next case. Where it
            //ends, or leaves one of the blocks it is in, a simple jump must
            //follow: the jump is written in its place
            lifted = slice(labelList, labelIndex, labelList.size());
            if (hasJumpTo(lifted, ids, false) || !resolveJumpsAfterBlocks(lifted, path, firstBlock, loopId)) {
                return false;
            }
            long innerId = nextId++;
            long outerId = nextId++;
            removeRange(labelList, labelIndex, labelList.size());
            if (canCompleteNormally(labelList)) {
                labelList.add(new BreakItem(dialect, null, null, innerId));
            }
            //a break of the loop in the moved code goes after the loop
            retargetBreaks(lifted, loopId, outerId);
            List<GraphTargetItem> innerCommands = new ArrayList<>();
            innerCommands.add(container);
            innerCommands.add(new BreakItem(dialect, null, null, outerId));
            List<GraphTargetItem> outerCommands = new ArrayList<>();
            outerCommands.add(new LabeledBlockItem(dialect, null, null, innerId, innerCommands));
            outerCommands.addAll(lifted);
            parentList.set(parentIndex, new LabeledBlockItem(dialect, null, null, outerId, outerCommands));
            return true;
        }
        return false;
    }

    /**
     * Checks whether the execution can reach the end of the commands.
     */
    private boolean canCompleteNormally(List<GraphTargetItem> list) {
        if (list == null || list.isEmpty()) {
            return true;
        }
        GraphTargetItem last = list.get(list.size() - 1);
        if (last instanceof ExitItem || last instanceof BreakItem || last instanceof ContinueItem) {
            return false;
        }
        if (last instanceof GotoItem) {
            GotoItem gi = (GotoItem) last;
            return gi.targetCommands != null && canCompleteNormally(gi.targetCommands);
        }
        if (last instanceof IfItem) {
            IfItem ifItem = (IfItem) last;
            if (ifItem.onTrue == null || ifItem.onTrue.isEmpty() || ifItem.onFalse == null || ifItem.onFalse.isEmpty()) {
                return true;
            }
            return canCompleteNormally(ifItem.onTrue) || canCompleteNormally(ifItem.onFalse);
        }
        if (last instanceof LabeledBlockItem) {
            LabeledBlockItem block = (LabeledBlockItem) last;
            Set<Long> ids = new HashSet<>();
            ids.add(block.id);
            return canCompleteNormally(block.commands) || hasJumpTo(block.commands, ids, true);
        }
        if (isLoopWithoutEnd(last)) {
            WhileItem whileItem = (WhileItem) last;
            Set<Long> ids = new HashSet<>();
            ids.add(whileItem.loop.id);
            return hasJumpTo(whileItem.commands, ids, true);
        }
        return true;
    }

    /**
     * Checks whether the item is a while(true): only a break leaves it.
     */
    private boolean isLoopWithoutEnd(GraphTargetItem item) {
        if (!(item instanceof WhileItem)) {
            return false;
        }
        WhileItem whileItem = (WhileItem) item;
        if (whileItem.loop == null || whileItem.commands == null) {
            return false;
        }
        return (item instanceof UniversalLoopItem)
                || (whileItem.expression != null && whileItem.expression.size() == 1 && (whileItem.expression.get(0) instanceof TrueItem));
    }

    /**
     * Checks whether the commands have a break (or a continue) of one of the
     * ids.
     */
    private boolean hasJumpTo(List<GraphTargetItem> list, Set<Long> ids, boolean breaks) {
        if (list == null) {
            return false;
        }
        for (GraphTargetItem item : list) {
            if (breaks && (item instanceof BreakItem) && ids.contains(((BreakItem) item).loopId)) {
                return true;
            }
            if (!breaks && (item instanceof ContinueItem) && ids.contains(((ContinueItem) item).loopId)) {
                return true;
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    if (hasJumpTo(sub, ids, breaks)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void retargetBreaks(List<GraphTargetItem> list, long fromId, long toId) {
        if (list == null) {
            return;
        }
        for (GraphTargetItem item : list) {
            if ((item instanceof BreakItem) && ((BreakItem) item).loopId == fromId) {
                ((BreakItem) item).loopId = toId;
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    retargetBreaks(sub, fromId, toId);
                }
            }
        }
    }

    /**
     * Replaces the gotos to the label with a break (or a continue) of the id.
     */
    private void replaceGotos(List<GraphTargetItem> list, String labelName, long id, boolean asContinue) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            GraphTargetItem item = list.get(i);
            if (item instanceof GotoItem) {
                GotoItem gi = (GotoItem) item;
                if (gi.targetCommands == null && labelName.equals(gi.labelName)) {
                    list.set(i, asContinue
                            ? new ContinueItem(dialect, null, null, id)
                            : new BreakItem(dialect, null, null, id));
                    continue;
                }
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    replaceGotos(sub, labelName, id, asContinue);
                }
            }
        }
    }

    private boolean findPath(List<GraphTargetItem> list, GraphTargetItem target, List<Step> path) {
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            GraphTargetItem item = list.get(i);
            if (item == target) {
                path.add(new Step(list, i));
                return true;
            }
            if (item instanceof Block) {
                path.add(new Step(list, i));
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    if (findPath(sub, target, path)) {
                        return true;
                    }
                }
                path.remove(path.size() - 1);
            }
        }
        return false;
    }

    private List<GotoItem> collectGotos(List<GraphTargetItem> list) {
        List<GotoItem> ret = new ArrayList<>();
        collectGotos(list, ret);
        return ret;
    }

    private void collectGotos(List<GraphTargetItem> list, List<GotoItem> ret) {
        if (list == null) {
            return;
        }
        for (GraphTargetItem item : list) {
            if (item instanceof GotoItem) {
                GotoItem gi = (GotoItem) item;
                if (gi.targetCommands == null && gi.labelName != null) {
                    ret.add(gi);
                }
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    collectGotos(sub, ret);
                }
            }
        }
    }

    /**
     * Finds the label, when there is exactly one with the name.
     */
    private LabelItem findLabel(List<GraphTargetItem> root, String labelName) {
        List<LabelItem> found = new ArrayList<>();
        findLabels(root, labelName, found);
        return found.size() == 1 ? found.get(0) : null;
    }

    private void findLabels(List<GraphTargetItem> list, String labelName, List<LabelItem> found) {
        if (list == null) {
            return;
        }
        for (GraphTargetItem item : list) {
            if ((item instanceof LabelItem) && labelName.equals(((LabelItem) item).labelName)) {
                found.add((LabelItem) item);
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    findLabels(sub, labelName, found);
                }
            }
        }
    }

    /**
     * A goto with the commands of its target is the label and the commands.
     */
    private void inlineTargetCommands(List<GraphTargetItem> list) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            GraphTargetItem item = list.get(i);
            if (item instanceof GotoItem) {
                GotoItem gi = (GotoItem) item;
                if (gi.targetCommands != null) {
                    List<GraphTargetItem> commands = new ArrayList<>();
                    if (gi.labelName != null) {
                        commands.add(new LabelItem(dialect, null, gi.lineStartItem, gi.labelName));
                    }
                    commands.addAll(gi.targetCommands);
                    list.remove(i);
                    insertAll(list, i, commands);
                    i--;
                    continue;
                }
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    inlineTargetCommands(sub);
                }
            }
        }
    }

    private long maxId(List<GraphTargetItem> list) {
        long max = 0;
        if (list == null) {
            return max;
        }
        for (GraphTargetItem item : list) {
            if (item instanceof BreakItem) {
                max = Math.max(max, ((BreakItem) item).loopId);
            }
            if (item instanceof ContinueItem) {
                max = Math.max(max, ((ContinueItem) item).loopId);
            }
            if ((item instanceof LoopItem) && ((LoopItem) item).loop != null) {
                max = Math.max(max, ((LoopItem) item).loop.id);
            }
            if (item instanceof LabeledBlockItem) {
                max = Math.max(max, ((LabeledBlockItem) item).id);
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    max = Math.max(max, maxId(sub));
                }
            }
        }
        return max;
    }

    /**
     * The lists of commands are of a class of its own, their sub lists are
     * not trustworthy: the commands are read, removed and inserted one by one.
     */
    private static List<GraphTargetItem> slice(List<GraphTargetItem> list, int from, int to) {
        List<GraphTargetItem> ret = new ArrayList<>();
        for (int n = from; n < to; n++) {
            ret.add(list.get(n));
        }
        return ret;
    }

    private static void removeRange(List<GraphTargetItem> list, int from, int to) {
        for (int n = to - 1; n >= from; n--) {
            list.remove(n);
        }
    }

    private static void insertAll(List<GraphTargetItem> list, int index, List<GraphTargetItem> items) {
        for (int n = 0; n < items.size(); n++) {
            list.add(index + n, items.get(n));
        }
    }


    private void removeUnusedLabels(List<GraphTargetItem> root) {
        Set<String> used = new HashSet<>();
        for (GotoItem g : collectGotos(root)) {
            used.add(g.labelName);
        }
        removeLabels(root, used);
    }

    private void removeLabels(List<GraphTargetItem> list, Set<String> used) {
        if (list == null) {
            return;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            GraphTargetItem item = list.get(i);
            if (item instanceof LabelItem) {
                String name = ((LabelItem) item).labelName;
                if (!used.contains(name)) {
                    list.remove(i);
                }
                continue;
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    removeLabels(sub, used);
                }
            }
        }
    }
}
