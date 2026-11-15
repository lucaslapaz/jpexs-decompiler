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
 * Declarations of locals placed at their first use instead of the top of the
 * method, where the compiler initializes them (as3LocalDeclarationsAtFirstUse).
 *
 * @author JPEXS
 */
public class ActionScript3LocalDeclarationPlacementTest extends ActionScript3DecompileTestBase {

    @BeforeClass
    public void init() throws IOException, InterruptedException {
        addSwf("classic", "testdata/as3_new/bin/as3_new.flex.swf");
        Configuration.as3LocalDeclarationsAtFirstUse.set(true);
    }

    @AfterClass
    public void finish() {
        Configuration.as3LocalDeclarationsAtFirstUse.set(false);
    }

    @Test
    public void testDeclarations() {
        decompileMethod("classic", "testDeclarations", "var vall:* = 6;\r\n"
                + "var vstr:String = \"hello\";\r\n"
                + "var vuint:uint = 7;\r\n"
                + "var vint:int = -4;\r\n"
                + "var vclass:TestClass1 = new TestClass1();\r\n"
                + "var vnumber:Number = 0.5;\r\n"
                + "vnumber = 6;\r\n"
                + "var vobject:Object = vclass;\r\n",
                false);
    }

    @Test
    public void testForEach() {
        decompileMethod("classic", "testForEach", "var list:Array = new Array();\r\n"
                + "list[0] = \"first\";\r\n"
                + "list[1] = \"second\";\r\n"
                + "list[2] = \"third\";\r\n"
                + "for each(var item in list)\r\n"
                + "{\r\n"
                + "trace(\"item #\" + item);\r\n"
                + "}\r\n",
                false);
    }

    @Test
    public void testForEachTry() {
        decompileMethod("classic", "testForEachTry", "var list:Object = {};\r\n"
                + "var b:Boolean = true;\r\n"
                + "for each(var name:String in list)\r\n"
                + "{\r\n"
                + "try\r\n"
                + "{\r\n"
                + "trace(\"xx\");\r\n"
                + "if(b)\r\n"
                + "{\r\n"
                + "trace(\"A\");\r\n"
                + "}\r\n"
                + "else\r\n"
                + "{\r\n"
                + "trace(\"B\");\r\n"
                + "}\r\n"
                + "}\r\n"
                + "catch(e:Error)\r\n"
                + "{\r\n"
                + "trace(\"C\");\r\n"
                + "}\r\n"
                + "trace(\"D\");\r\n"
                + "}\r\n",
                false);
    }

    @Test
    public void testForIn() {
        decompileMethod("classic", "testForIn", "var item:* = null;\r\n"
                + "var dic:Dictionary = null;\r\n"
                + "for(item in dic)\r\n"
                + "{\r\n"
                + "trace(item);\r\n"
                + "}\r\n"
                + "for each(item in dic)\r\n"
                + "{\r\n"
                + "trace(item);\r\n"
                + "}\r\n",
                false);
    }

    @Test
    public void testLoopInLoop() {
        decompileMethod("classic", "testLoopInLoop", "var a:Boolean = true;\r\n"
                + "var b:Boolean = true;\r\n"
                + "for(var c:Boolean = true; true; )\r\n"
                + "{\r\n"
                + "trace(\"A\");\r\n"
                + "for(var i:int = 0; i < 10; i++)\r\n"
                + "{\r\n"
                + "if(!a)\r\n"
                + "{\r\n"
                + "trace(\"B\");\r\n"
                + "if(c)\r\n"
                + "{\r\n"
                + "trace(\"C\");\r\n"
                + "}\r\n"
                + "else\r\n"
                + "{\r\n"
                + "trace(\"D\");\r\n"
                + "if(b)\r\n"
                + "{\r\n"
                + "continue;\r\n"
                + "}\r\n"
                + "trace(\"H\");\r\n"
                + "}\r\n"
                + "if(c)\r\n"
                + "{\r\n"
                + "trace(\"L\");\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "if(a)\r\n"
                + "{\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "}\r\n",
                false);
    }

    @Test
    public void testTryReturn() {
        decompileMethod("classic", "testTryReturn", "try\r\n"
                + "{\r\n"
                + "var i:int = 0;\r\n"
                + "var b:Boolean = true;\r\n"
                + "if(i > 0)\r\n"
                + "{\r\n"
                + "while(this.testDoWhile2())\r\n"
                + "{\r\n"
                + "if(b)\r\n"
                + "{\r\n"
                + "return 5;\r\n"
                + "}\r\n"
                + "}\r\n"
                + "}\r\n"
                + "i++;\r\n"
                + "return 2;\r\n"
                + "}\r\n"
                + "catch(e:Error)\r\n"
                + "{\r\n"
                + "}\r\n"
                + "return 4;\r\n",
                false);
    }

    @Test
    public void testAlwaysBreak() {
        decompileMethod("classic", "testAlwaysBreak", "var v:int = 5;\r\n"
                + "trace(\"a\");\r\n"
                + "while(true)\r\n"
                + "{\r\n"
                + "if(v > 4)\r\n"
                + "{\r\n"
                + "trace(\"b\");\r\n"
                + "if(v > 10)\r\n"
                + "{\r\n"
                + "trace(\"c\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "trace(\"d\");\r\n"
                + "}\r\n"
                + "trace(\"e\");\r\n"
                + "break;\r\n"
                + "}\r\n"
                + "trace(\"f\");\r\n",
                false);
    }

    @Test
    public void testInnerFunctions() {
        decompileMethod("classic", "testInnerFunctions", "var s:int = 0;\r\n"
                + "var innerFunc:Function = function(b:String):*\r\n"
                + "{\r\n"
                + "trace(b);\r\n"
                + "};\r\n"
                + "var k:int = 5;\r\n"
                + "if(k == 6)\r\n"
                + "{\r\n"
                + "s = 8;\r\n"
                + "}\r\n"
                + "innerFunc(a);\r\n",
                false);
    }
}
