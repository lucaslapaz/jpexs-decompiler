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
package com.jpexs.decompiler.flash;

import com.jpexs.decompiler.flash.action.Action;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.helpers.CodeFormatting;
import com.jpexs.decompiler.flash.helpers.HighlightedTextWriter;
import com.jpexs.decompiler.flash.tags.DoActionTag;
import com.jpexs.decompiler.flash.tags.ShowFrameTag;
import com.jpexs.decompiler.flash.tags.Tag;
import com.jpexs.helpers.utf8.Utf8Helper;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Names of AS1/2 registers without name taken from the context
 * (as12LocalNamesFromContext).
 *
 * @author JPEXS
 */
public class ActionScript2LocalNamesTest extends ActionScript2TestBase {

    @BeforeClass
    public void init() throws IOException, InterruptedException {
        Configuration.autoDeobfuscate.set(false);
        Configuration.simplifyExpressions.set(false);
        Configuration.decompile.set(true);
        Configuration.registerNameFormat.set("_loc%d_");
        Configuration.autoRenameIdentifiers.set(false);
        Configuration.skipDetectionOfUninitializedClassFields.set(false);
        Configuration.as12LocalNamesFromContext.set(true);
        swf = new SWF(new BufferedInputStream(new FileInputStream("testdata/as2/as2.swf")), false);
    }

    @AfterClass
    public void finish() {
        Configuration.as12LocalNamesFromContext.set(false);
    }

    private void compareSrc(int frame, String expectedResult) {
        DoActionTag doa = getFrameSource(frame);
        assertNotNull(doa);
        HighlightedTextWriter writer = new HighlightedTextWriter(new CodeFormatting(), false);
        try {
            Action.actionsToSource(new HashMap<>(), doa, doa.getActions(), "", writer, Utf8Helper.charsetName);
        } catch (InterruptedException ex) {
            fail();
        }
        writer.finishHilights();
        String actualResult = cleanPCode(writer.toString());
        expectedResult = cleanPCode(expectedResult);
        assertEquals(actualResult, expectedResult);
    }

    private DoActionTag getFrameSource(int frame) {
        int f = 0;
        DoActionTag lastDoa = null;
        for (Tag t : swf.getTags()) {
            if (t instanceof DoActionTag) {
                lastDoa = (DoActionTag) t;
            }
            if (t instanceof ShowFrameTag) {
                f++;
                if (f == frame) {
                    return lastDoa;
                }
                lastDoa = null;
            }
        }
        return null;
    }

    @Test
    public void frame40_forInTest() {
        compareSrc(40, "function testForIn()\r\n"
                + "{\r\n"
                + "var arr = [];\r\n"
                + "for(var key in arr)\r\n"
                + "{\r\n"
                + "if(key > 3)\r\n"
                + "{\r\n"
                + "if(key == 5)\r\n"
                + "{\r\n"
                + "return 7;\r\n"
                + "}\r\n"
                + "return 8;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "trace(\"forInTest\");\r\n"
                + "trace(testForIn());\r\n"
                + "var arr = [];\r\n"
                + "for(var a in arr)\r\n"
                + "{\r\n"
                + "trace(a);\r\n"
                + "}\r\n");
    }

    @Test
    public void frame45_function2Test() {
        compareSrc(45, "function a()\r\n"
                + "{\r\n"
                + "trace(\"hi\");\r\n"
                + "var value = 5;\r\n"
                + "if(value == 7)\r\n"
                + "{\r\n"
                + "return undefined;\r\n"
                + "}\r\n"
                + "value *= 9;\r\n"
                + "trace(value);\r\n"
                + "}\r\n"
                + "trace(\"function2Test\");\r\n");
    }

    @Test
    public void frame48_forInInTest() {
        compareSrc(48, "function tst()\r\n"
                + "{\r\n"
                + "var arr = [];\r\n"
                + "arr[0] = [];\r\n"
                + "for(var key2 in arr)\r\n"
                + "{\r\n"
                + "for(var key in key2)\r\n"
                + "{\r\n"
                + "if(key == 5)\r\n"
                + "{\r\n"
                + "return 5;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "if(key2 == 8)\r\n"
                + "{\r\n"
                + "return 3;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "return 8;\r\n"
                + "}\r\n"
                + "trace(\"forInInTest\");\r\n"
                + "tst();\r\n");
    }

    @Test
    public void frame51_function3Test() {
        compareSrc(51, "function tst()\r\n"
                + "{\r\n"
                + "var c2 = 5;\r\n"
                + "c = c2 = 8;\r\n"
                + "trace(\"hi\");\r\n"
                + "trace(c2);\r\n"
                + "if((e = d = f = c = 9) > 5)\r\n"
                + "{\r\n"
                + "trace(\"dd\");\r\n"
                + "}\r\n"
                + "}\r\n"
                + "trace(\"function3Test\");\r\n"
                + "var c = 7;\r\n"
                + "var d = 7;\r\n"
                + "var e = 8;\r\n"
                + "tst();\r\n");
    }

    @Test
    public void frame54_function4Test() {
        compareSrc(54, "function tst()\r\n"
                + "{\r\n"
                + "var value = 5;\r\n"
                + "while(value < 10)\r\n"
                + "{\r\n"
                + "if(value != 5)\r\n"
                + "{\r\n"
                + "return false;\r\n"
                + "}\r\n"
                + "if(value == 6)\r\n"
                + "{\r\n"
                + "return true;\r\n"
                + "}\r\n"
                + "value = value + 1;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "trace(\"function4Test\");\r\n"
                + "tst();\r\n");
    }

    @Test
    public void frame63_functionVariablesTest() {
        compareSrc(63, "function outfunc()\r\n"
                + "{\r\n"
                + "var func = function()\r\n"
                + "{\r\n"
                + "var a = 5;\r\n"
                + "var value = 6;\r\n"
                + "var func = function()\r\n"
                + "{\r\n"
                + "var result = a + 2;\r\n"
                + "trace(result);\r\n"
                + "return result;\r\n"
                + "};\r\n"
                + "return func();\r\n"
                + "};\r\n"
                + "return func();\r\n"
                + "}\r\n"
                + "trace(\"functionVariablesTest\");\r\n");
    }

    @Test
    public void frame64_functionInnerParametersTest() {
        compareSrc(64, "function outfunc()\r\n"
                + "{\r\n"
                + "var func = function(a, x)\r\n"
                + "{\r\n"
                + "var value = a + 3 + x;\r\n"
                + "var func = function()\r\n"
                + "{\r\n"
                + "var result = a + 2;\r\n"
                + "trace(result);\r\n"
                + "return result;\r\n"
                + "};\r\n"
                + "return func();\r\n"
                + "};\r\n"
                + "return func(5,2);\r\n"
                + "}\r\n"
                + "trace(\"functionInnerParametersTest\");\r\n");
    }

    @Test
    public void frame66_forInBreakTest() {
        compareSrc(66, "function testFunc1()\r\n"
                + "{\r\n"
                + "for(var key in obj)\r\n"
                + "{\r\n"
                + "trace(key);\r\n"
                + "}\r\n"
                + "}\r\n"
                + "function testFunc2()\r\n"
                + "{\r\n"
                + "for(var key in obj)\r\n"
                + "{\r\n"
                + "if(key == \"b\")\r\n"
                + "{\r\n"
                + "trace(\"found\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "function testFunc3()\r\n"
                + "{\r\n"
                + "for(var key in obj)\r\n"
                + "{\r\n"
                + "if(key == \"b\")\r\n"
                + "{\r\n"
                + "trace(\"hi\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "trace(\"after\");\r\n"
                + "}\r\n"
                + "function testFunc4()\r\n"
                + "{\r\n"
                + "for(var key in obj)\r\n"
                + "{\r\n"
                + "if(key == \"b\")\r\n"
                + "{\r\n"
                + "trace(\"hi\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "if(key == \"c\")\r\n"
                + "{\r\n"
                + "trace(\"hello\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "trace(\"hohoho\");\r\n"
                + "}\r\n"
                + "trace(\"after\");\r\n"
                + "}\r\n"
                + "function testFunc5()\r\n"
                + "{\r\n"
                + "var obj2 = {key1:1,key2:2,key3:3};\r\n"
                + "for(var key2 in obj)\r\n"
                + "{\r\n"
                + "if(key2 == \"a\")\r\n"
                + "{\r\n"
                + "trace(\"loop1_break\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "if(key2 == \"b\")\r\n"
                + "{\r\n"
                + "trace(\"hello\");\r\n"
                + "for(var key in obj2)\r\n"
                + "{\r\n"
                + "if(key == \"key1\")\r\n"
                + "{\r\n"
                + "trace(\"loop2_break\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "trace(\"loop2_inside\");\r\n"
                + "}\r\n"
                + "trace(\"after_loop2\");\r\n"
                + "}\r\n"
                + "trace(\"loop1_inside\");\r\n"
                + "}\r\n"
                + "trace(\"after_loop1\");\r\n"
                + "}\r\n"
                + "function testFunc6()\r\n"
                + "{\r\n"
                + "var obj2 = {key1:1,key2:2,key3:3};\r\n"
                + "for(var key2 in obj)\r\n"
                + "{\r\n"
                + "if(key2 == \"a\")\r\n"
                + "{\r\n"
                + "trace(\"loop1_break\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "trace(\"hello\");\r\n"
                + "for(var key in obj2)\r\n"
                + "{\r\n"
                + "if(key == \"key1\")\r\n"
                + "{\r\n"
                + "trace(\"loop2_break\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "trace(\"loop2_inside\");\r\n"
                + "}\r\n"
                + "trace(\"after_loop2\");\r\n"
                + "}\r\n"
                + "trace(\"after_loop1\");\r\n"
                + "}\r\n"
                + "trace(\"forInBreakTest\");\r\n"
                + "var obj = {a:5,b:6,c:7};\r\n");
    }

    @Test
    public void frame79_registersVsDefineLocalTest() {
        compareSrc(79, "function x1(c)\r\n"
                + "{\r\n"
                + "var value2 = 1;\r\n"
                + "var value = 2;\r\n"
                + "return value2 + value * c;\r\n"
                + "}\r\n"
                + "function x2(c)\r\n"
                + "{\r\n"
                + "var a0 = 1;\r\n"
                + "var a1 = 2;\r\n"
                + "var r = Math.floor(Math.random() * 2);\r\n"
                + "return eval(\"a\" + r) + b * c;\r\n"
                + "}\r\n"
                + "function x3(c)\r\n"
                + "{\r\n"
                + "var _loc2_ = 1;\r\n"
                + "var _loc3_ = 2;\r\n"
                + "var _loc1_ = Math.floor(Math.random() * 2);\r\n"
                + "set(\"a\" + _loc1_,12);\r\n"
                + "return _loc2_ + b * c;\r\n"
                + "}\r\n"
                + "trace(\"registersVsDefineLocalTest\");\r\n"
                + "trace(x1(2) + x2(3) + x3(4));\r\n");
    }

    @Test
    public void frame89_functionPostIncrementTest() {
        compareSrc(89, "function myFunc()\r\n"
                + "{\r\n"
                + "var value = 0;\r\n"
                + "var obj = {};\r\n"
                + "var value3 = obj[value++];\r\n"
                + "var value2 = obj[value--];\r\n"
                + "}\r\n"
                + "trace(\"functionPostIncrementTest\");\r\n");
    }

    @Test
    public void frame95_breakDetectionTest() {
        compareSrc(95, "function myFunction(item)\r\n"
                + "{\r\n"
                + "var arr;\r\n"
                + "var value;\r\n"
                + "if(!isNaN(item))\r\n"
                + "{\r\n"
                + "arr = [1,2,3];\r\n"
                + "for(var key in arr)\r\n"
                + "{\r\n"
                + "switch(key)\r\n"
                + "{\r\n"
                + "case \"A\":\r\n"
                + "if(item == \"a\")\r\n"
                + "{\r\n"
                + "return true;\r\n"
                + "}\r\n"
                + "case \"B\":\r\n"
                + "if(item == \"b\")\r\n"
                + "{\r\n"
                + "return true;\r\n"
                + "}\r\n"
                + "case \"C\":\r\n"
                + "if(item == \"c\")\r\n"
                + "{\r\n"
                + "return true;\r\n"
                + "}\r\n"
                + "case \"D\":\r\n"
                + "if(item == \"d\")\r\n"
                + "{\r\n"
                + "return true;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "else\r\n"
                + "{\r\n"
                + "trace(\"item is nan\");\r\n"
                + "}\r\n"
                + "return false;\r\n"
                + "}\r\n"
                + "trace(\"breakDetectionTest\");\r\n");
    }
}
