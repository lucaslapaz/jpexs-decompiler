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
package com.jpexs.decompiler.flash.as3decompile;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.abc.ABC;
import com.jpexs.decompiler.flash.abc.ScriptPack;
import com.jpexs.decompiler.flash.abc.avm2.instructions.AVM2Instruction;
import com.jpexs.decompiler.flash.abc.avm2.instructions.debug.DebugIns;
import com.jpexs.decompiler.flash.abc.types.ConvertData;
import com.jpexs.decompiler.flash.abc.types.MethodBody;
import com.jpexs.decompiler.flash.abc.types.Multiname;
import com.jpexs.decompiler.flash.abc.types.traits.Trait;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.exporters.modes.ScriptExportMode;
import com.jpexs.decompiler.flash.helpers.CodeFormatting;
import com.jpexs.decompiler.flash.helpers.HighlightedTextWriter;
import com.jpexs.decompiler.flash.helpers.NulWriter;
import com.jpexs.decompiler.flash.tags.ABCContainerTag;
import com.jpexs.decompiler.graph.DottedChain;
import com.jpexs.decompiler.graph.ScopeStack;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Parameters named paramN in debug info (obfuscators write them) get a
 * generated name, which the method header must use like the body does.
 *
 * @author JPEXS
 */
public class ActionScript3GeneratedParamNamesTest {

    private boolean paramNamesEnable;

    @BeforeClass
    public void init() {
        Configuration.autoDeobfuscate.set(false);
        Configuration.decompile.set(true);
        Configuration.showMethodBodyId.set(false);
        Configuration.as3LocalNamesFromContext.set(true);
        Configuration.getLocalNamesFromDebugInfo.set(true);
        paramNamesEnable = Configuration.paramNamesEnable.get();
        Configuration.paramNamesEnable.set(false);
    }

    @AfterClass
    public void finish() {
        Configuration.as3LocalNamesFromContext.set(false);
        Configuration.paramNamesEnable.set(paramNamesEnable);
    }

    private SWF swf;

    private ScriptPack scriptPack;

    private ABC abc;

    private int classIndex;

    private MethodBody body;

    private void load(String className) throws Exception {
        swf = new SWF(new BufferedInputStream(new FileInputStream("testdata/as3_new/bin/as3_new.flex.swf")), false);
        List<ABC> abcs = new ArrayList<>();
        for (ABCContainerTag abcTag : swf.getAbcList()) {
            abcs.add(abcTag.getABC());
        }
        scriptPack = null;
        for (ABC a : abcs) {
            scriptPack = a.findScriptPackByPath("tests." + className, abcs);
            if (scriptPack != null) {
                break;
            }
        }
        assertNotNull(scriptPack);
        abc = scriptPack.abc;
        classIndex = abc.findClassByName(new DottedChain(new String[]{"tests", className}));
        body = abc.bodies.get(abc.findMethodBodyByName(classIndex, "run"));
    }

    /**
     * Decompiles the run method.
     *
     * @return Header (parameters) and body
     */
    private String[] decompile() throws Exception {
        List<MethodBody> callStack = new ArrayList<>();
        callStack.add(body);
        Set<String> usedDeobfuscations = new LinkedHashSet<>();
        body.convert(swf.version, callStack, swf.getAbcIndex(), new ConvertData(), "run", ScriptExportMode.AS, false, body.method_info, scriptPack.scriptIndex, classIndex, abc, null, new ScopeStack(), 0, new NulWriter(), new ArrayList<>(), abc.instance_info.get(classIndex).instance_traits, true, new HashSet<>(), new ArrayList<>(), usedDeobfuscations);

        HighlightedTextWriter header = new HighlightedTextWriter(new CodeFormatting(), false);
        abc.method_info.get(body.method_info).getParamStr(header, abc.constants, body, abc, new ArrayList<>(), usedDeobfuscations);
        header.finishHilights();

        HighlightedTextWriter code = new HighlightedTextWriter(new CodeFormatting(), false);
        body.toString(usedDeobfuscations, swf.version, callStack, swf.getAbcIndex(), "run", ScriptExportMode.AS, abc, null, code, new ArrayList<>(), new HashSet<>(), -1);
        code.finishHilights();
        return new String[]{header.toString(), code.toString()};
    }

    @Test
    public void testParamNamesFromDebugInfo() throws Exception {
        load("TestParamNames");
        //debug 1, "firstp", 0, 6 -> debug 1, "param1", 0, 6
        int renamed = 0;
        for (AVM2Instruction ins : body.getCode().code) {
            if ((ins.definition instanceof DebugIns) && ins.operands[0] == 1) {
                int reg = ins.operands[2] + 1;
                ins.operands[1] = abc.constants.getStringId("param" + reg, true);
                renamed++;
            }
        }
        assertEquals(renamed, 3);

        String[] result = decompile();
        assertEquals(result[0], "num:int, num2:int, num3:int");
        assertTrue(result[1].contains("return num + num2 + num3;"), result[1]);
    }

    @Test
    public void testParamCopiedToSlotOfSameName() throws Exception {
        load("TestInnerFunctions");
        //the parameter a is copied to the activation slot a; name the slot param1,
        //like obfuscators do, so the copy is left out and param1 is both
        Configuration.getLocalNamesFromDebugInfo.set(false);
        try {
            boolean renamed = false;
            for (Trait t : body.traits.traits) {
                Multiname multiname = abc.constants.getMultiname(t.name_index);
                if ("a".equals(abc.constants.getString(multiname.name_index))) {
                    multiname.name_index = abc.constants.getStringId("param1", true);
                    renamed = true;
                }
            }
            assertTrue(renamed);

            String[] result = decompile();
            assertEquals(result[0], "param1:String");
            assertTrue(result[1].contains("innerFunc(param1);"), result[1]);
        } finally {
            Configuration.getLocalNamesFromDebugInfo.set(true);
        }
    }
}
