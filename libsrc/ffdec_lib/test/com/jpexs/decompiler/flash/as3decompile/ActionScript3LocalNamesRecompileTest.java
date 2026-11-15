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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Code decompiled with generated local names and declarations at first use
 * compiles again.
 *
 * @author JPEXS
 */
public class ActionScript3LocalNamesRecompileTest {

    @BeforeClass
    public void init() {
        Configuration.autoDeobfuscate.set(false);
        Configuration.simplifyExpressions.set(false);
        Configuration._debugCopy.set(false);
        Configuration.useFlexAs3Compiler.set(false);
        Configuration.decompile.set(true);
        Configuration.as3LocalDeclarationsAtFirstUse.set(true);
        Configuration.as3LocalNamesFromContext.set(true);
        Configuration.getLocalNamesFromDebugInfo.set(false);
    }

    @AfterClass
    public void finish() {
        Configuration.as3LocalDeclarationsAtFirstUse.set(false);
        Configuration.as3LocalNamesFromContext.set(false);
        Configuration.getLocalNamesFromDebugInfo.set(true);
    }

    @DataProvider(name = "swfs")
    public Object[][] provideSwfs() {
        return new Object[][]{
            {"testdata/as3_new/bin/as3_new.flex.swf"},
            {"testdata/as3_new/bin/as3_new.air.swf"}
        };
    }

    @Test(dataProvider = "swfs")
    public void testRecompile(String path) throws Exception {
        assertNotNull(Configuration.getPlayerSWC(), "Player SWC library not found");
        SWF swf = new SWF(new BufferedInputStream(new FileInputStream(path)), false);
        List<ABC> allAbcs = new ArrayList<>();
        for (ABCContainerTag ct : swf.getAbcList()) {
            allAbcs.add(ct.getABC());
        }
        for (ABC abc : allAbcs) {
            for (int s = 0; s < abc.script_info.size(); s++) {
                ScriptPack pack = abc.script_info.get(s).getPacks(abc, s, null, allAbcs).get(0);
                HighlightedTextWriter writer = new HighlightedTextWriter(new CodeFormatting(), false);
                pack.toSource(swf.getAbcIndex(), writer, abc.script_info.get(s).traits.traits, new ConvertData(), ScriptExportMode.AS, false, false, false);
                writer.finishHilights();
                String source = writer.toString();
                try {
                    abc.replaceScriptPack(As3ScriptReplacerFactory.createFFDec(), pack, source, new ArrayList<>());
                } catch (Exception ex) {
                    fail("Does not compile: " + pack.getClassPath() + ": " + ex.getMessage() + "\n" + source, ex);
                }
            }
        }
    }
}
