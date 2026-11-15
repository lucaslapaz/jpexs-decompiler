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
import com.jpexs.decompiler.flash.abc.types.ConvertData;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.exporters.modes.ScriptExportMode;
import com.jpexs.decompiler.flash.helpers.CodeFormatting;
import com.jpexs.decompiler.flash.helpers.HighlightedTextWriter;
import com.jpexs.decompiler.flash.importers.As3ScriptReplacerFactory;
import com.jpexs.decompiler.flash.tags.ABCContainerTag;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The value of a call to a function without a getter prefix would have the name
 * of the function itself, which is in use: it gets the Result suffix instead of
 * a number, which would look like another function.
 *
 * @author JPEXS
 */
public class ActionScript3CallResultNamesTest {

    @BeforeClass
    public void init() {
        Configuration.autoDeobfuscate.set(false);
        Configuration.decompile.set(true);
        Configuration.useFlexAs3Compiler.set(false);
        Configuration.as3LocalDeclarationsAtFirstUse.set(true);
        Configuration.as3LocalNamesFromContext.set(true);
    }

    @AfterClass
    public void finish() {
        Configuration.as3LocalDeclarationsAtFirstUse.set(false);
        Configuration.as3LocalNamesFromContext.set(false);
    }

    private static ScriptPack findPack(SWF swf, String path) {
        List<ABC> abcs = new ArrayList<>();
        for (ABCContainerTag tag : swf.getAbcList()) {
            abcs.add(tag.getABC());
        }
        for (ABC abc : abcs) {
            ScriptPack pack = abc.findScriptPackByPath(path, abcs);
            if (pack != null) {
                return pack;
            }
        }
        return null;
    }

    private static String source(SWF swf, ScriptPack pack) throws Exception {
        HighlightedTextWriter writer = new HighlightedTextWriter(new CodeFormatting(), false);
        pack.toSource(swf.getAbcIndex(), writer, pack.abc.script_info.get(pack.scriptIndex).traits.traits, new ConvertData(), ScriptExportMode.AS, false, false, false);
        writer.finishHilights();
        return writer.toString();
    }

    @Test
    public void testResultOfGlobalFunction() throws Exception {
        assertNotNull(Configuration.getPlayerSWC(), "Player SWC library not found");
        SWF swf = new SWF(new BufferedInputStream(new FileInputStream("testdata/as3_new/bin/as3_new.flex.swf")), false);
        ScriptPack pack = findPack(swf, "tests.TestHello");
        assertNotNull(pack);
        String code = "package tests\n"
                + "{\n"
                + "   public class TestHello\n"
                + "   {\n"
                + "      public function run(param1:String) : void\n"
                + "      {\n"
                + "         var _loc2_:String = decodeURI(param1);\n"
                + "         trace(_loc2_.length);\n"
                + "      }\n"
                + "   }\n"
                + "}\n";
        pack.abc.replaceScriptPack(As3ScriptReplacerFactory.createFFDec(), pack, code, new ArrayList<>());
        swf.clearScriptCache();
        String result = source(swf, findPack(swf, "tests.TestHello"));
        assertTrue(result.contains("var decodeURIResult:String = decodeURI("), result);
        assertFalse(result.contains("decodeURI2"), result);
    }
}
