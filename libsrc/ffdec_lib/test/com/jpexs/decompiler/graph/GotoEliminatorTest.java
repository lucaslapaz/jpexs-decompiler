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

import com.jpexs.decompiler.flash.abc.avm2.graph.AVM2GraphTargetDialect;
import com.jpexs.decompiler.flash.abc.avm2.model.IntegerValueAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ReturnVoidAVM2Item;
import com.jpexs.decompiler.graph.model.BreakItem;
import com.jpexs.decompiler.graph.model.ContinueItem;
import com.jpexs.decompiler.graph.model.GotoItem;
import com.jpexs.decompiler.graph.model.IfItem;
import com.jpexs.decompiler.graph.model.LabelItem;
import com.jpexs.decompiler.graph.model.LabeledBlockItem;
import com.jpexs.decompiler.graph.model.UniversalLoopItem;
import com.jpexs.decompiler.graph.model.WhileItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests of the gotos written as blocks with a label, on trees of commands made
 * by hand (shapes which the decompiler gives only for large methods). The tree
 * is run before and after, with the same results of the conditions, and the
 * commands run must be the same.
 */
public class GotoEliminatorTest {

    private final GraphTargetDialect dialect = AVM2GraphTargetDialect.INSTANCE;

    private static List<GraphTargetItem> commands(GraphTargetItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /**
     * A command (a number which is written to the trace) or a condition (a
     * number which takes the next result).
     */
    private GraphTargetItem value(int n) {
        return new IntegerValueAVM2Item(null, null, n);
    }

    private IfItem ifThen(int condition, GraphTargetItem... onTrue) {
        return new IfItem(dialect, null, null, value(condition), commands(onTrue), commands());
    }

    private BreakItem breakOf(long id) {
        return new BreakItem(dialect, null, null, id);
    }

    private ContinueItem continueOf(long id) {
        return new ContinueItem(dialect, null, null, id);
    }

    private UniversalLoopItem loop(long id, GraphTargetItem... body) {
        return new UniversalLoopItem(dialect, null, null, new Loop(id, null, null), commands(body));
    }

    private LabeledBlockItem block(long id, GraphTargetItem... body) {
        return new LabeledBlockItem(dialect, null, null, id, commands(body));
    }

    private LabelItem label(String name) {
        return new LabelItem(dialect, null, null, name);
    }

    private GotoItem gotoLabel(String name) {
        return new GotoItem(dialect, null, null, name);
    }

    private ReturnVoidAVM2Item ret() {
        return new ReturnVoidAVM2Item(null, null);
    }

    /**
     * How a command ends: normally, or by a jump.
     */
    private static class Completion {

        static final Completion NORMAL = new Completion(0, 0, null);

        static final Completion RETURN = new Completion(3, 0, null);

        /**
         * 0 normal, 1 break, 2 continue, 3 return, 4 goto
         */
        final int kind;

        final long id;

        final String label;

        Completion(int kind, long id, String label) {
            this.kind = kind;
            this.id = id;
            this.label = label;
        }
    }

    private static class StepLimit extends RuntimeException {
    }

    /**
     * Runs a tree of commands.
     */
    private static class Runner {

        final List<GraphTargetItem> root;

        final Random random;

        final List<Integer> trace = new ArrayList<>();

        int steps = 0;

        Runner(List<GraphTargetItem> root, long seed) {
            this.root = root;
            this.random = new Random(seed);
        }

        List<Integer> run() {
            try {
                Completion c = runList(root, 0);
                while (c.kind == 4) {
                    List<Integer> path = new ArrayList<>();
                    if (!find(root, c.label, path)) {
                        throw new AssertionError("no label " + c.label);
                    }
                    c = runFrom(root, path, 0);
                }
            } catch (StepLimit ex) {
                trace.add(-1);
            }
            return trace;
        }

        boolean condition(GraphTargetItem expression) {
            step();
            trace.add(1000 + ((Integer) ((IntegerValueAVM2Item) expression).value));
            return random.nextInt(3) != 0;
        }

        void step() {
            if (++steps > 200) {
                throw new StepLimit();
            }
        }

        Completion runList(List<GraphTargetItem> list, int from) {
            for (int i = from; i < list.size(); i++) {
                Completion c = runItem(list.get(i));
                if (c.kind != 0) {
                    return c;
                }
            }
            return Completion.NORMAL;
        }

        /**
         * Runs the list from the command of the path at the depth, entering
         * into it down to the label.
         */
        Completion runFrom(List<GraphTargetItem> list, List<Integer> path, int depth) {
            int index = path.get(depth);
            if (depth == path.size() - 1) {
                return runList(list, index);
            }
            GraphTargetItem item = list.get(index);
            int sub = path.get(depth + 1);
            List<GraphTargetItem> subList = ((Block) item).getSubs().get(sub);
            Completion c;
            if (item instanceof LabeledBlockItem) {
                c = runFrom(subList, path, depth + 2);
                if (c.kind == 1 && c.id == ((LabeledBlockItem) item).id) {
                    c = Completion.NORMAL;
                }
            } else if (item instanceof IfItem) {
                c = runFrom(subList, path, depth + 2);
            } else if (item instanceof WhileItem) {
                c = runFrom(subList, path, depth + 2);
                c = loopRest((WhileItem) item, c);
            } else {
                throw new AssertionError("label in " + item.getClass());
            }
            if (c.kind != 0) {
                return c;
            }
            return runList(list, index + 1);
        }

        Completion loopRest(WhileItem loop, Completion c) {
            while (true) {
                if (c.kind == 1 && c.id == loop.loop.id) {
                    return Completion.NORMAL;
                }
                if (c.kind != 0 && !(c.kind == 2 && c.id == loop.loop.id)) {
                    return c;
                }
                if (!(loop instanceof UniversalLoopItem) && !condition(loop.expression.get(0))) {
                    return Completion.NORMAL;
                }
                c = runList(loop.commands, 0);
            }
        }

        Completion runItem(GraphTargetItem item) {
            if (item instanceof IntegerValueAVM2Item) {
                step();
                trace.add((Integer) ((IntegerValueAVM2Item) item).value);
                return Completion.NORMAL;
            }
            if (item instanceof LabelItem) {
                return Completion.NORMAL;
            }
            if (item instanceof BreakItem) {
                return new Completion(1, ((BreakItem) item).loopId, null);
            }
            if (item instanceof ContinueItem) {
                return new Completion(2, ((ContinueItem) item).loopId, null);
            }
            if (item instanceof ReturnVoidAVM2Item) {
                return Completion.RETURN;
            }
            if (item instanceof GotoItem) {
                return new Completion(4, 0, ((GotoItem) item).labelName);
            }
            if (item instanceof IfItem) {
                IfItem ifItem = (IfItem) item;
                return runList(condition(ifItem.expression) ? ifItem.onTrue : ifItem.onFalse, 0);
            }
            if (item instanceof LabeledBlockItem) {
                Completion c = runList(((LabeledBlockItem) item).commands, 0);
                if (c.kind == 1 && c.id == ((LabeledBlockItem) item).id) {
                    return Completion.NORMAL;
                }
                return c;
            }
            if (item instanceof WhileItem) {
                WhileItem loop = (WhileItem) item;
                if (!(loop instanceof UniversalLoopItem) && !condition(loop.expression.get(0))) {
                    return Completion.NORMAL;
                }
                return loopRest(loop, runList(loop.commands, 0));
            }
            throw new AssertionError("command " + item.getClass());
        }

        static boolean find(List<GraphTargetItem> list, String labelName, List<Integer> path) {
            for (int i = 0; i < list.size(); i++) {
                GraphTargetItem item = list.get(i);
                if ((item instanceof LabelItem) && ((LabelItem) item).labelName.equals(labelName)) {
                    path.add(i);
                    return true;
                }
                if (item instanceof Block) {
                    List<List<GraphTargetItem>> subs = ((Block) item).getSubs();
                    for (int s = 0; s < subs.size(); s++) {
                        if (subs.get(s) == null) {
                            continue;
                        }
                        path.add(i);
                        path.add(s);
                        if (find(subs.get(s), labelName, path)) {
                            return true;
                        }
                        path.remove(path.size() - 1);
                        path.remove(path.size() - 1);
                    }
                }
            }
            return false;
        }
    }

    private static boolean hasGoto(List<GraphTargetItem> list) {
        for (GraphTargetItem item : list) {
            if (item instanceof GotoItem) {
                return true;
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    if (sub != null && hasGoto(sub)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Rewrites the gotos of the tree, checks that none is left and that the
     * tree does the same as before.
     */
    private void assertGotosRemovedKeepingFlow(List<GraphTargetItem> tree) {
        List<List<Integer>> before = new ArrayList<>();
        for (int seed = 0; seed < 300; seed++) {
            before.add(new Runner(tree, seed).run());
        }
        new GotoEliminator(dialect).eliminate(tree);
        Assert.assertFalse(hasGoto(tree), "goto left");
        for (int seed = 0; seed < 300; seed++) {
            Assert.assertEquals(new Runner(tree, seed).run(), before.get(seed), "seed " + seed);
        }
    }

    @Test
    public void testGotoFromAfterLoopIntoBlockOfItsBody() throws Exception {
        //the label is in a block in the body of a loop, the code after the
        //block ends the loop with a return, and a goto from after the loop
        //goes to the label
        List<GraphTargetItem> tree = commands(
                loop(1,
                        ifThen(1, breakOf(1)),
                        block(2,
                                ifThen(2, breakOf(2)),
                                value(10),
                                ifThen(3, continueOf(1)),
                                label("addr1"),
                                value(11)),
                        value(20),
                        ret()),
                value(30),
                gotoLabel("addr1"));
        assertGotosRemovedKeepingFlow(tree);
    }

    @Test
    public void testLoopExitCodeMovedOutOfInnerLoopForGotoFromAfterOuterLoop() throws Exception {
        //the label is in a block in the body of an inner loop, whose code after
        //the block returns, and the goto comes from after the outer loop: the
        //code which returns goes out of the inner loop, then the code of the
        //label can leave both loops
        List<GraphTargetItem> tree = commands(
                loop(1,
                        ifThen(1, breakOf(1)),
                        loop(3,
                                ifThen(5, breakOf(3)),
                                block(2,
                                        ifThen(2, breakOf(2)),
                                        value(10),
                                        ifThen(3, continueOf(3)),
                                        label("addr1"),
                                        value(11)),
                                value(20),
                                ret()),
                        value(21)),
                value(30),
                gotoLabel("addr1"));
        assertGotosRemovedKeepingFlow(tree);
    }
}
