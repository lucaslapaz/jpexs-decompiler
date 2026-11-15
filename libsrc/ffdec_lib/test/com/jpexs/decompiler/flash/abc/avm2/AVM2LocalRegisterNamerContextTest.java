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

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.abc.ABC;
import com.jpexs.decompiler.flash.abc.avm2.graph.AVM2GraphTargetDialect;
import com.jpexs.decompiler.flash.abc.avm2.model.FullMultinameAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.IntegerValueAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.LocalRegAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NullAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ThisAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.AddAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.ModuloAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.MultiplyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.NeqAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.SubtractAVM2Item;
import com.jpexs.decompiler.flash.abc.types.MethodBody;
import com.jpexs.decompiler.flash.tags.ABCContainerTag;
import com.jpexs.decompiler.graph.GraphTargetItem;
import com.jpexs.decompiler.graph.TypeItem;
import com.jpexs.decompiler.graph.model.TernarOpItem;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Names from the context of values: owners of generic properties, operations,
 * comparisons and the priority between them.
 *
 * @author JPEXS
 */
public class AVM2LocalRegisterNamerContextTest {

    private ABC abc;

    private MethodBody body;

    @BeforeClass
    public void init() throws Exception {
        SWF swf = new SWF(new BufferedInputStream(new FileInputStream("testdata/as3_new/bin/as3_new.flex.swf")), false);
        List<ABC> abcs = new ArrayList<>();
        for (ABCContainerTag abcTag : swf.getAbcList()) {
            abcs.add(abcTag.getABC());
        }
        //a method without parameters: its registers 1... are locals
        for (ABC a : abcs) {
            int classIndex = a.findClassByName(new com.jpexs.decompiler.graph.DottedChain(new String[]{"tests", "TestHello"}));
            if (classIndex > -1) {
                abc = a;
                body = a.bodies.get(a.findMethodBodyByName(classIndex, "run"));
            }
        }
    }

    private static GraphTargetItem thisItem() {
        return new ThisAVM2Item(null, null, null, false, false);
    }

    private static FullMultinameAVM2Item name(String name) {
        return new FullMultinameAVM2Item(true, null, null, 0, name);
    }

    /**
     * this.a.b...
     */
    private static GraphTargetItem property(String... names) {
        GraphTargetItem ret = thisItem();
        for (String n : names) {
            ret = new GetPropertyAVM2Item(null, null, ret, name(n), TypeItem.UNBOUNDED, null, false);
        }
        return ret;
    }

    private static GraphTargetItem local(int reg) {
        return new LocalRegAVM2Item(null, null, reg, null, TypeItem.UNBOUNDED);
    }

    private static GraphTargetItem integer(int value) {
        return new IntegerValueAVM2Item(null, null, value);
    }

    private static SetLocalAVM2Item set(int reg, GraphTargetItem value) {
        return new SetLocalAVM2Item(null, null, reg, value, TypeItem.UNBOUNDED);
    }

    private Map<Integer, String> name(List<GraphTargetItem> items) {
        return new AVM2LocalRegisterNamer(abc, body, null, new HashMap<>()).name(items);
    }

    @Test
    public void testOwnerOfGenericProperty() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, property("player", "x")));
        items.add(set(2, property("player", "y")));
        //not generic: no owner
        items.add(set(3, property("player", "speed")));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(1), "playerX");
        assertEquals(names.get(2), "playerY");
        assertEquals(names.get(3), "speed");
    }

    @Test
    public void testOwnerBeforeNumber() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, property("player", "speed")));
        items.add(set(2, property("enemy", "speed")));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(1), "speed");
        assertEquals(names.get(2), "enemySpeed", "the owner instead of speed2");
    }

    @Test
    public void testSumWhenTheNameIsInUse() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, property("x")));
        //x + offset is still an x: xValue, not x2 and not a name from the type
        items.add(set(2, new AddAVM2Item(null, null, property("x"), property("parent", "x"))));
        //x * 2 is another quantity: no name from x
        items.add(set(3, new MultiplyAVM2Item(null, null, property("x"), integer(2))));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(1), "x");
        assertEquals(names.get(2), "xValue");
        assertNotEquals(names.get(3), "xValue2");
        assertFalse(names.get(3).startsWith("x"), names.get(3));
    }

    @Test
    public void testDifference() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, property("player", "x")));
        items.add(set(2, new SubtractAVM2Item(null, null, property("enemy", "x"), local(1))));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(2), "dx");
    }

    @Test
    public void testComparisonWithNull() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, new NeqAVM2Item(null, null, property("tp1", "stage"), new NullAVM2Item(null, null))));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(1), "hasTp1Stage");
    }

    @Test
    public void testOperationsWithoutMisleadingNames() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, new MultiplyAVM2Item(null, null, property("speed"), integer(2))));
        //an operation on a local is not that local
        items.add(set(2, new ModuloAVM2Item(null, null, local(1), integer(60))));
        //the name of an operation is not numbered
        items.add(set(3, new MultiplyAVM2Item(null, null, property("speed"), integer(3))));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(1), "speed");
        assertNotEquals(names.get(2), "speed2");
        assertNotEquals(names.get(3), "speed2");
    }

    @Test
    public void testStoreBeforeOperation() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, new SubtractAVM2Item(null, null, property("start"), integer(1000))));
        items.add(new SetPropertyAVM2Item(null, null, thisItem(), name("elapsed"), local(1), TypeItem.UNBOUNDED, null, false));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(1), "elapsed");
    }

    @Test
    public void testConditionOfLiteralTernary() {
        List<GraphTargetItem> items = new ArrayList<>();
        items.add(set(1, new TernarOpItem(AVM2GraphTargetDialect.INSTANCE, null, null, property("jumping"), integer(1), integer(0))));
        Map<Integer, String> names = name(items);
        assertEquals(names.get(1), "jumping");
    }
}
