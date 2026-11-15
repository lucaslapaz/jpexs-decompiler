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

import com.jpexs.decompiler.flash.ActionScript3DecompileTestBase;
import com.jpexs.decompiler.flash.configuration.Configuration;
import java.io.IOException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Names of locals and parameters without name taken from the context
 * (as3LocalNamesFromContext). The debug names of the test SWF are ignored, so
 * the registers have no name.
 *
 * @author JPEXS
 */
public class ActionScript3LocalNamesTest extends ActionScript3DecompileTestBase {

    @BeforeClass
    public void init() throws IOException, InterruptedException {
        addSwf("classic", "testdata/as3_new/bin/as3_new.flex.swf");
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

    @Test
    public void testForEach() {
        decompileMethod("classic", "testForEach", "var arr:Array = new Array();\r\n"
                + "arr[0] = \"first\";\r\n"
                + "arr[1] = \"second\";\r\n"
                + "arr[2] = \"third\";\r\n"
                + "for each(var item in arr)\r\n"
                + "{\r\n"
                + "trace(\"item #\" + item);\r\n"
                + "}\r\n",
                false);
    }

    @Test
    public void testFor() {
        decompileMethod("classic", "testFor", "for(var i:* = 0; i < 10; i++)\r\n"
                + "{\r\n"
                + "trace(\"a=\" + i);\r\n"
                + "}\r\n",
                false);
    }

    @Test
    public void testLoopInLoop() {
        decompileMethod("classic", "testLoopInLoop", "var flag:Boolean = true;\r\n"
                + "var flag2:Boolean = true;\r\n"
                + "for(var flag3:Boolean = true; true; )\r\n"
                + "{\r\n"
                + "trace(\"A\");\r\n"
                + "for(var i:int = 0; i < 10; i++)\r\n"
                + "{\r\n"
                + "if(!flag)\r\n"
                + "{\r\n"
                + "trace(\"B\");\r\n"
                + "if(flag3)\r\n"
                + "{\r\n"
                + "trace(\"C\");\r\n"
                + "}\r\n"
                + "else\r\n"
                + "{\r\n"
                + "trace(\"D\");\r\n"
                + "if(flag2)\r\n"
                + "{\r\n"
                + "continue;\r\n"
                + "}\r\n"
                + "trace(\"H\");\r\n"
                + "}\r\n"
                + "if(flag3)\r\n"
                + "{\r\n"
                + "trace(\"L\");\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "if(flag)\r\n"
                + "{\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "}\r\n",
                false);
    }

    @Test
    public void testDeclarations() {
        decompileMethod("classic", "testDeclarations", "var value:* = 6;\r\n"
                + "var str:String = \"hello\";\r\n"
                + "var num2:uint = 7;\r\n"
                + "var num:int = -4;\r\n"
                + "var testClass1:TestClass1 = new TestClass1();\r\n"
                + "var num3:Number = 0.5;\r\n"
                + "num3 = 6;\r\n"
                + "var obj:Object = testClass1;\r\n",
                false);
    }

    @Test
    public void testCallLocal() {
        decompileMethod("classic", "testCallLocal", "var f:Function = this.getF();\r\n"
                + "var num:int = f(1,3);\r\n",
                false);
    }

    @Test
    public void testVector() {
        decompileMethod("classic", "testVector", "var vec:Vector.<String> = new Vector.<String>();\r\n"
                + "vec.push(\"hello\");\r\n"
                + "vec[0] = \"hi\";\r\n"
                + "var num:int = 5;\r\n"
                + "vec[num * 8 - 39] = \"hi2\";\r\n"
                + "trace(vec[0]);\r\n",
                false);
    }

    @Test
    public void testTryReturn() {
        decompileMethod("classic", "testTryReturn", "try\r\n"
                + "{\r\n"
                + "var num:int = 0;\r\n"
                + "var flag:Boolean = true;\r\n"
                + "if(num > 0)\r\n"
                + "{\r\n"
                + "while(this.testDoWhile2())\r\n"
                + "{\r\n"
                + "if(flag)\r\n"
                + "{\r\n"
                + "return 5;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "num++;\r\n"
                + "return 2;\r\n"
                + "}\r\n"
                + "catch(e:Error)\r\n"
                + "{\r\n"
                + "}\r\n"
                + "return 4;\r\n",
                false);
    }

    @Test
    public void testInnerFunctions() {
        decompileMethod("classic", "testInnerFunctions", "var a:String = a2;\r\n"
                + "var s:int = 0;\r\n"
                + "var innerFunc:Function = function(str:String):*\r\n"
                + "{\r\n"
                + "trace(str);\r\n"
                + "};\r\n"
                + "var k:int = 5;\r\n"
                + "if(k == 6)\r\n"
                + "{\r\n"
                + "s = 8;\r\n"
                + "}\r\n"
                + "innerFunc(a);\r\n",
                false);
    }

    @Test
    public void testParamNames() {
        decompileMethod("classic", "testParamNames", "return num + num2 + num3;\r\n",
                false);
    }

    @Test
    public void testNames() {
        decompileMethod("classic", "testNames", "var value:* = this.getNamespace();\r\n"
                + "var name:* = this.getName();\r\n"
                + "var value2:* = value::unnamespacedFunc();\r\n"
                + "var value3:* = value::[String(name)];\r\n"
                + "trace(value3.c);\r\n"
                + "var neco2:* = myInternal::neco;\r\n"
                + "var neco3:* = this.myInternal2::neco;\r\n",
                false);
    }
}
