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

import com.jpexs.decompiler.flash.abc.ABC;
import com.jpexs.decompiler.flash.abc.ScriptPack;
import com.jpexs.decompiler.flash.abc.avm2.deobfuscation.AVM2DeobfuscatorSimpleOld;
import com.jpexs.decompiler.flash.abc.avm2.AVM2Code;
import com.jpexs.decompiler.flash.abc.avm2.deobfuscation.AVM2DeobfuscatorGroupParts;
import com.jpexs.decompiler.flash.abc.avm2.deobfuscation.AVM2DeobfuscatorJumps;
import com.jpexs.decompiler.flash.abc.avm2.parser.AVM2ParseException;
import com.jpexs.decompiler.flash.abc.avm2.parser.pcode.ASM3Parser;
import com.jpexs.decompiler.flash.abc.avm2.parser.pcode.MissingSymbolHandler;
import com.jpexs.decompiler.flash.abc.avm2.parser.script.AbcIndexing;
import com.jpexs.decompiler.flash.abc.avm2.parser.script.ActionScript3Parser;
import com.jpexs.decompiler.flash.abc.types.ABCException;
import com.jpexs.decompiler.flash.abc.types.ClassInfo;
import com.jpexs.decompiler.flash.abc.types.ConvertData;
import com.jpexs.decompiler.flash.abc.types.Float4;
import com.jpexs.decompiler.flash.abc.types.InstanceInfo;
import com.jpexs.decompiler.flash.abc.types.MethodBody;
import com.jpexs.decompiler.flash.abc.types.MethodInfo;
import com.jpexs.decompiler.flash.abc.types.Multiname;
import com.jpexs.decompiler.flash.abc.types.Namespace;
import com.jpexs.decompiler.flash.abc.types.ScriptInfo;
import com.jpexs.decompiler.flash.abc.types.ValueKind;
import com.jpexs.decompiler.flash.abc.types.traits.Traits;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.exporters.modes.ScriptExportMode;
import com.jpexs.decompiler.flash.helpers.CodeFormatting;
import com.jpexs.decompiler.flash.helpers.HighlightedTextWriter;
import com.jpexs.decompiler.flash.helpers.SWFDecompilerAdapter;
import com.jpexs.decompiler.flash.tags.ABCContainerTag;
import com.jpexs.decompiler.graph.CompilationException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;
import macromedia.asc.util.Decimal128;
import org.testng.Assert;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 *
 * @author JPEXS
 */
public class ActionScript3DeobfuscatorTest extends ActionScriptTestBase {

    protected SWF swf;

    @BeforeClass
    public void init() throws IOException, InterruptedException {
        //Main.initLogging(false);
        Configuration.autoDeobfuscate.set(true);
        Configuration.decimalAddress.set(false);
        Configuration.decompilationTimeoutSingleMethod.set(Integer.MAX_VALUE);
        Configuration.padAs3PCodeInstructionName.set(false);
        Configuration.useOldStyleGetSetLocalsAs3PCode.set(false);
        Configuration.labelOnSeparateLineAs3PCode.set(true);
        Configuration.decompilationTimeoutSingleMethod.set(5);
        swf = new SWF(new BufferedInputStream(new FileInputStream("testdata/as3/as3.swf")), false);
    }

    private void decompilePCode(String pCode, String expected) throws Exception {
        decompilePCode(pCode, expected, true);
    }

    /**
     * Decompiles p-code without deobfuscation, keeping debugline instructions
     * (line based structures like for loops need them).
     */
    private void decompilePCodeWithLines(String pCode, String expected) throws Exception {
        boolean autoDeobfuscate = Configuration.autoDeobfuscate.get();
        Configuration.autoDeobfuscate.set(false);
        try {
            decompilePCode(pCode, expected, false);
        } finally {
            Configuration.autoDeobfuscate.set(autoDeobfuscate);
        }
    }

    private void decompilePCode(String pCode, String expected, boolean deobfuscate) throws Exception {
        assertEquals(decompilePCodeSource(pCode, deobfuscate), expected);
    }

    private String decompilePCodeSource(String pCode, boolean deobfuscate) throws Exception {
        pCode = "code\r\n"
                + "getlocal0\r\n"
                + "pushscope\r\n"
                + pCode
                + "returnvoid\r\n";
        final ABC abc = new ABC(new ABCContainerTag() {
            @Override
            public ABC getABC() {
                return null;
            }

            @Override
            public SWF getSwf() {
                return swf;
            }

            @Override
            public int compareTo(ABCContainerTag o) {
                return 0;
            }

            @Override
            public void setABC(ABC abc) {
            }
        });
        MethodBody b = new MethodBody(abc, new Traits(), new byte[0], new ABCException[0]);
        AVM2Code code = ASM3Parser.parse(abc, new StringReader(pCode), null, new MissingSymbolHandler() {
            //no longer ask for adding new constants
            @Override
            public boolean missingString(String value) {
                return true;
            }

            @Override
            public boolean missingInt(long value) {
                return true;
            }

            @Override
            public boolean missingUInt(long value) {
                return true;
            }

            @Override
            public boolean missingDouble(double value) {
                return true;
            }

            @Override
            public boolean missingDecimal(Decimal128 value) {
                return true;
            }

            @Override
            public boolean missingFloat(float value) {
                return true;
            }

            @Override
            public boolean missingFloat4(Float4 value) {
                return true;
            }
        }, b, new MethodInfo());
        b.setCode(code);
        abc.addMethodBody(b);
        abc.addMethodInfo(new MethodInfo(new int[]{0}, 0, 0, 0, new ValueKind[0], new int[0]));

        ClassInfo ci = new ClassInfo();
        InstanceInfo ii = new InstanceInfo();
        ii.name_index = abc.constants.addMultiname(
                Multiname.createQName(
                        false,
                        abc.constants.getStringId("Test", true),
                        abc.constants.getNamespaceId(Namespace.KIND_PACKAGE, "", 0, true)
                ));
        abc.addClass(ci, ii, 0);
        ScriptInfo si = new ScriptInfo(new Traits());
        abc.script_info.add(si);

        if (deobfuscate) {
            code.removeTraps(null, 0, b, abc, 0, -1, true, pCode);
            code.removeLabelsAndDebugLine(b);
        }
        HighlightedTextWriter writer = new HighlightedTextWriter(new CodeFormatting(), false);
        String actual = b.toSource(new LinkedHashSet<>(), 10, new ArrayList<>(), swf.getAbcIndex(), 0, new HashSet<>(), -1);
        return actual.replace("\r\n", "\n");
    }

    private String recompilePCode(String str, SWFDecompilerAdapter deobfuscator) throws IOException, AVM2ParseException, InterruptedException {
        str = "code\r\n"
                + "getlocal0\r\n"
                + "pushscope\r\n"
                + str
                + "returnvoid\r\n";
        final ABC abc = new ABC(new ABCContainerTag() {
            @Override
            public ABC getABC() {
                return null;
            }

            @Override
            public SWF getSwf() {
                return swf;
            }

            @Override
            public int compareTo(ABCContainerTag o) {
                return 0;
            }

            @Override
            public void setABC(ABC abc) {
            }
        });
        MethodBody b = new MethodBody(abc, new Traits(), new byte[0], new ABCException[0]);
        AVM2Code code = ASM3Parser.parse(abc, new StringReader(str), null, b, new MethodInfo());
        b.setCode(code);
        abc.addMethodInfo(new MethodInfo(new int[]{0}, 0, 0, 0, new ValueKind[0], new int[0]));
        deobfuscator.avm2CodeRemoveTraps("test", 0, true, 0, abc, null, 0, b);
        HighlightedTextWriter writer = new HighlightedTextWriter(new CodeFormatting(), false);
        code.toASMSource(abc, abc.constants, new MethodInfo(), new MethodBody(abc, new Traits(), new byte[0], new ABCException[0]), ScriptExportMode.PCODE, writer);
        writer.finishHilights();
        String ret = writer.toString();
        ret = ret.replaceAll("\r\n +", "\r\n");
        String prefix = "\r\ncode\r\n";
        String suffix = "end ; code";
        return ret.substring(ret.lastIndexOf(prefix) + prefix.length(), ret.lastIndexOf(suffix));
    }

    private String recompile(String str) throws AVM2ParseException, IOException, CompilationException, InterruptedException {
        str = "package { public class Test {  public static function trace(s){ } public static function test(){ " + str + " }   }  }";
        return recompileSource(str);
    }

    private String recompileSource(String str) throws AVM2ParseException, IOException, CompilationException, InterruptedException {
        return recompileSource(str, null);
    }

    private String recompileSource(String str, String scriptInitPCode) throws AVM2ParseException, IOException, CompilationException, InterruptedException {
        final ABC abc = new ABC(new ABCContainerTag() {
            @Override
            public ABC getABC() {
                return null;
            }

            @Override
            public SWF getSwf() {
                return swf;
            }

            @Override
            public int compareTo(ABCContainerTag o) {
                return 0;
            }

            @Override
            public void setABC(ABC abc) {
            }
        });
        AbcIndexing index = swf.getAbcIndex();
        index.addAbc(abc);
        index.rebuildPkgToObjectsNameMap();
        ActionScript3Parser par = new ActionScript3Parser(index);
        HighlightedTextWriter writer = new HighlightedTextWriter(new CodeFormatting(), false);
        par.addScript(str, "Test.as", 0, 0, swf.getDocumentClass(), abc);

        List<ScriptPack> packs = abc.script_info.get(0).getPacks(abc, 0, "", new ArrayList<>());
        if (scriptInitPCode != null) {
            MethodBody body = abc.findBody(abc.script_info.get(0).init_index);
            body.setCode(ASM3Parser.parse(abc, new StringReader(scriptInitPCode), null, new MissingSymbolHandler() {
                @Override
                public boolean missingString(String value) {
                    return true;
                }

                @Override
                public boolean missingInt(long value) {
                    return true;
                }

                @Override
                public boolean missingUInt(long value) {
                    return true;
                }

                @Override
                public boolean missingDouble(double value) {
                    return true;
                }

                @Override
                public boolean missingFloat(float value) {
                    return true;
                }

                @Override
                public boolean missingDecimal(Decimal128 value) {
                    return true;
                }

                @Override
                public boolean missingFloat4(Float4 value) {
                    return true;
                }
            }, body, abc.method_info.get(abc.script_info.get(0).init_index)));
        }
        for (ScriptPack pack : scriptInitPCode != null ? packs : packs.subList(0, 1)) {
            pack.toSource(swf.getAbcIndex(), writer, abc.script_info.get(0).traits.traits, new ConvertData(), ScriptExportMode.AS, false, false, false);
        }
        writer.finishHilights();
        return writer.toString();
    }

    @DataProvider(name = "provideBasicTrueExpressions")
    public Object[][] provideBasicTrueExpressions() {
        return new Object[][]{
            {"1!=5"}, {"5==5"}, {"1<4"}, {"5>4"}, {"5*6==30"}
        };
    }

    @DataProvider(name = "provideBasicFalseExpressions")
    public Object[][] provideBasicFalseExpressions() {
        return new Object[][]{
            {"1==5"}, {"5!=5"}, {"1>4"}, {"5<4"}, {"5*7==12"}
        };
    }

    @Test(dataProvider = "provideBasicTrueExpressions")
    public void testRemoveBasicTrueExpressions(String expression) throws IOException, CompilationException, InterruptedException, TimeoutException, AVM2ParseException {
        String res = recompile("if(" + expression + "){"
                + "trace(\"OK\");"
                + "} else {"
                + "trace(\"FAIL\");"
                + "}");
        if (res.contains("\"FAIL\"")) {
            fail("OnFalse clause was not removed: " + res);
        }
        if (!res.contains("\"OK\"")) {
            fail("OnTrue clause was removed: " + res);
        }
    }

    @Test(dataProvider = "provideBasicFalseExpressions")
    public void testRemoveBasicFalseExpressions(String expression) throws Exception {
        String res = recompile("if(" + expression + "){"
                + "trace(\"FAIL\");"
                + "} else {"
                + "trace(\"OK\");"
                + "}");
        if (res.contains("\"FAIL\"")) {
            fail("OnTrue clause was not removed:" + res);
        }
        if (!res.contains("\"OK\"")) {
            fail("OnFalse clause was removed:" + res);
        }
    }

    @Test
    public void testRemoveKnownVariables() throws Exception {
        String res = recompile("var a = true; var b = false;"
                + "if(a){"
                + "trace(\"OK1\");"
                + "}else{"
                + "trace(\"FAIL1\");"
                + "}"
                + "if(b){"
                + "trace(\"FAIL2\");"
                + "}else{"
                + "trace(\"OK2\");"
                + "}");
        if (!res.contains("\"OK1\"")) {
            fail("if true OnTrue removed");
        }
        if (!res.contains("\"OK2\"")) {
            fail("if false OnFalse removed");
        }
        if (res.contains("\"FAIL1\"")) {
            fail("if true OnFalse not removed:");
        }
        if (res.contains("\"FAIL2\"")) {
            fail("if false OnTrue not removed");
        }
        if (res.contains("var ")) {
            fail("variables for obfuscation not removed");
        }
        if (res.contains("if")) {
            fail("if clauses not removed");
        }
    }

    @Test
    public void testRemoveKnownVariables2() throws Exception {
        String res = recompile("var a = true; var b = false;"
                + "if(a){"
                + "trace(\"OK1\");"
                + "}else{"
                + "trace(\"FAIL1\");"
                + "}"
                //TODO:
                //+ "a = 59;"
                + "if(b){"
                + "trace(\"FAIL2\");"
                + "}else{"
                + "trace(\"OK2\");"
                + "}");
        if (!res.contains("\"OK1\"")) {
            fail("!OK1:" + res);
        }
        if (res.contains("\"FAIL1\"")) {
            fail("FAIL1");
        }
        if (!res.contains("\"OK2\"")) {
            fail("!OK2");
        }
        if (res.contains("\"FAIL2\"")) {
            fail("FAIL2");
        }
    }

    @Test
    public void testJumps() throws Exception {
        String res = recompilePCode("pushbyte 3\r\n"
                + "pushbyte 4\r\n"
                + "ifeq a\r\n" //should change to ifeq c
                + "jump b\r\n" //should not change
                + "a:jump c\r\n"
                + "c:pushbyte 4\r\n"
                + "b:pushbyte 3\r\n", new AVM2DeobfuscatorJumps());
        Assert.assertEquals(res, "getlocal0\r\n"
                + "pushscope\r\n"
                + "pushbyte 3\r\n"
                + "pushbyte 4\r\n"
                + "ifeq ofs000e\r\n"
                + "jump ofs0010\r\n"
                + "ofs000e:\r\n"
                + "pushbyte 4\r\n"
                + "ofs0010:\r\n"
                + "pushbyte 3\r\n"
                + "returnvoid\r\n");
    }

    @Test
    public void testGroupParts() throws Exception {
        String res = recompilePCode(
                "pushbyte 1\r\npop\r\n"
                + "jump A\r\n"
                + "B:pushbyte 3\r\npop\r\n"
                + "jump C\r\n"
                + "A:pushbyte 2\r\npop\r\n"
                + "jump B\r\n"
                + "C:pushbyte 4\r\npop\r\n", new AVM2DeobfuscatorGroupParts());
        Assert.assertEquals(res, "getlocal0\r\n"
                + "pushscope\r\n"
                + "pushbyte 1\r\n"
                + "pop\r\n"
                + "pushbyte 2\r\n"
                + "pop\r\n"
                + "pushbyte 3\r\n"
                + "pop\r\n"
                + "pushbyte 4\r\n"
                + "pop\r\n"
                + "returnvoid\r\n");
    }

    @Test
    public void testGroupParts2() throws Exception {
        String res = recompilePCode(
                "pushbyte 1\r\npop\r\n"
                + "jump A\r\n"
                + "B:pushbyte 3\r\npop\r\n"
                + "jump C\r\n"
                + "A:pushbyte 2\r\npop\r\n"
                + "jump B\r\n"
                + "C:pushbyte 4\r\npop\r\n", new AVM2DeobfuscatorGroupParts());
        Assert.assertEquals(res, "getlocal0\r\n"
                + "pushscope\r\n"
                + "pushbyte 1\r\n"
                + "pop\r\n"
                + "pushbyte 2\r\n"
                + "pop\r\n"
                + "pushbyte 3\r\n"
                + "pop\r\n"
                + "pushbyte 4\r\n"
                + "pop\r\n"
                + "returnvoid\r\n");
    }

    @Test
    public void testIgnoreLabels() throws Exception {
        decompilePCode("getlocal1\n"
                + "            setlocal2\n"
                + "            pushfalse\n"
                + "            setlocal3\n"
                + "            pushbyte 0\n"
                + "            convert_u\n"
                + "            setlocal 4\n"
                + "            jump ofs00a4\n"
                + "   ofs000f:\n"
                + "            label\n"
                + "            jump ofs001d\n"
                + "   ofs0014:\n"
                + "            label\n"
                + "            getlocal2\n"
                + "            pushstring \"C\"\n"
                + "            ifne ofs001d\n"
                + "   ofs001c:\n"
                + "            label\n" //this label critical to be removed
                + "   ofs001d:\n"
                + "            returnvoid\n"
                + "            pushtrue\n"
                + "            iftrue ofs0023\n"
                + "   ofs0023:\n"
                + "            label\n"
                + "            getlocal0\n"
                + "            getlocal1\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump ofs002d\n"
                + "   ofs002d:\n"
                + "            label\n"
                + "            jump ofs000f\n"
                + "            getlocal0\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            getlocal1\n"
                + "            callpropvoid QName(Namespace(\"http://adobe.com/AS3/2006/builtin\"),\"push\"), 1\n"
                + "            jump ofs003d\n"
                + "   ofs003d:\n"
                + "            label\n"
                + "            getlocal 4\n"
                + "            increment\n"
                + "            convert_u\n"
                + "            setlocal 4\n"
                + "            jump ofs0048\n"
                + "   ofs0048:\n"
                + "            label\n"
                + "            jump ofs002d\n"
                + "            label\n"
                + "            getlocal1\n"
                + "            getlocal0\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"xxx\")\n"
                + "            getlocal 4\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"xxx\")\n"
                + "            ifne ofs005c\n"
                + "   ofs005c:\n"
                + "            pushtrue\n"
                + "            setlocal3\n"
                + "            jump ofs0062\n"
                + "   ofs0062:\n"
                + "            label\n"
                + "            jump ofs001c\n"
                + "   ofs0067:\n"
                + "            label\n"
                + "            getlocal2\n"
                + "            pushstring \"B\"\n"
                + "            ifne ofs0014\n"
                + "            pushbyte 0\n"
                + "            convert_u\n"
                + "            setlocal 4\n"
                + "            jump ofs0048\n"
                + "   ofs0078:\n"
                + "            label\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            getlocal1\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump ofs0062\n"
                + "   ofs0083:\n"
                + "            label\n"
                + "            getlocal3\n"
                + "            pushfalse\n"
                + "            ifne ofs0062\n"
                + "            jump ofs0078\n"
                + "            label\n"
                + "            getlocal 4\n"
                + "            increment\n"
                + "            convert_u\n"
                + "            setlocal 4\n"
                + "            jump ofs0099\n"
                + "   ofs0099:\n"
                + "            label\n"
                + "            jump ofs0083\n"
                + "            pushtrue\n"
                + "            setlocal3\n"
                + "            jump ofs00a4\n"
                + "   ofs00a4:\n"
                + "            getlocal2\n"
                + "            pushstring \"A\"\n"
                + "            ifne ofs0067\n"
                + "            pushbyte 0\n"
                + "            convert_u\n"
                + "            setlocal 4\n"
                + "            jump ofs0099\n", "         var _loc2_:* = param1;\n"
                + "         var _loc4_:uint = 0;\n"
                + "         if(_loc2_ == \"A\")\n"
                + "         {\n"
                + "            _loc4_ = 0;\n"
                + "            trace(param1);\n"
                + "         }\n"
                + "         else if(_loc2_ == \"B\")\n"
                + "         {\n"
                + "            _loc4_ = 0;\n"
                + "         }\n"
                + "         else if(_loc2_ == \"C\")\n"
                + "         {\n"
                 + "         }\n");
    }

    @Test
    public void testSequentialStrictComparisonsNotSwitch() throws Exception {
        decompilePCode("getlocal1\n"
                + "            pushstring \"A\"\n"
                + "            ifstrictne checkB\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"first\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   checkB:\n"
                + "            getlocal1\n"
                + "            pushstring \"B\"\n"
                + "            ifstrictne checkC\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"second\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   checkC:\n"
                + "            getlocal1\n"
                + "            pushstring \"C\"\n"
                + "            ifstrictne finish\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"third\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   finish:\n",
                "         if(param1 === \"A\")\n"
                + "         {\n"
                + "            trace(\"first\");\n"
                + "         }\n"
                + "         if(param1 === \"B\")\n"
                + "         {\n"
                + "            trace(\"second\");\n"
                + "         }\n"
                + "         if(param1 === \"C\")\n"
                + "         {\n"
                + "            trace(\"third\");\n"
                + "         }\n");
    }

    @Test
    public void testSwitchWithInterleavedCaseBodies() throws Exception {
        decompilePCode("getlocal1\n"
                + "            pushstring \"A\"\n"
                + "            ifstrictne checkB\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"first\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump finish\n"
                + "   checkB:\n"
                + "            getlocal1\n"
                + "            pushstring \"B\"\n"
                + "            ifstrictne checkC\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"second\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump finish\n"
                + "   checkC:\n"
                + "            getlocal1\n"
                + "            pushstring \"C\"\n"
                + "            ifstrictne finish\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"third\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   finish:\n",
                "         switch(param1)\n"
                + "         {\n"
                + "            case \"A\":\n"
                + "               trace(\"first\");\n"
                + "               break;\n"
                + "            case \"B\":\n"
                + "               trace(\"second\");\n"
                + "               break;\n"
                + "            case \"C\":\n"
                + "               trace(\"third\");\n"
                + "         }\n");
    }

    @Test
    public void testSwitchFollowedByStrictComparison() throws Exception {
        decompilePCode("getlocal1\n"
                + "            pushstring \"A\"\n"
                + "            ifstrictne checkB\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"first\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump afterSwitch\n"
                + "   checkB:\n"
                + "            getlocal1\n"
                + "            pushstring \"B\"\n"
                + "            ifstrictne afterSwitch\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"second\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   afterSwitch:\n"
                + "            getlocal1\n"
                + "            pushstring \"C\"\n"
                + "            ifstrictne finish\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"after\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   finish:\n",
                "         switch(param1)\n"
                + "         {\n"
                + "            case \"A\":\n"
                + "               trace(\"first\");\n"
                + "               break;\n"
                + "            case \"B\":\n"
                + "               trace(\"second\");\n"
                + "         }\n"
                + "         if(param1 === \"C\")\n"
                + "         {\n"
                + "            trace(\"after\");\n"
                + "         }\n");
    }

    @Test
    public void testLoopBreakUsesMostCommonExit() throws Exception {
        decompilePCode("            pushbyte 0\n"
                + "            setlocal2\n"
                + "            pushbyte 0\n"
                + "            setlocal3\n"
                + "   condition:\n"
                + "            getlocal2\n"
                + "            pushbyte 10\n"
                + "            ifge after\n"
                + "            getlocal1\n"
                + "            pushstring \"A\"\n"
                + "            ifstricteq found\n"
                + "            getlocal1\n"
                + "            pushstring \"B\"\n"
                + "            ifstricteq found\n"
                + "            inclocal_i 2\n"
                + "            jump condition\n"
                + "   found:\n"
                + "            pushbyte 1\n"
                + "            setlocal3\n"
                + "            jump after\n"
                + "   after:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            getlocal3\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n",
                "         var _loc2_:int = 0;\n"
                + "         var _loc3_:int = 0;\n"
                + "         loop0:\n"
                + "         while(_loc2_ < 10)\n"
                + "         {\n"
                + "            switch(param1)\n"
                + "            {\n"
                + "               case \"A\":\n"
                + "               case \"B\":\n"
                + "                  _loc3_ = 1;\n"
                + "                  break loop0;\n"
                + "            }\n"
                + "            _loc2_++;\n"
                + "         }\n"
                + "         trace(_loc3_);\n");
    }

    @Test
    public void testNestedLoopOuterContinueDoesNotBecomeInnerBreak() throws Exception {
        decompilePCode("            pushbyte 0\n"
                + "            setlocal2\n"
                + "   outer_condition:\n"
                + "            getlocal2\n"
                + "            pushbyte 3\n"
                + "            ifge done\n"
                + "            pushbyte 0\n"
                + "            setlocal3\n"
                + "   inner_condition:\n"
                + "            getlocal3\n"
                + "            pushbyte 3\n"
                + "            ifge after_inner\n"
                + "            getlocal1\n"
                + "            iftrue outer_continue\n"
                + "            inclocal_i 3\n"
                + "            jump inner_condition\n"
                + "   after_inner:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"after inner\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   outer_continue:\n"
                + "            inclocal_i 2\n"
                + "            jump outer_condition\n"
                + "   done:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"done\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n",
                "         var _loc2_:int = 0;\n"
                + "         loop0:\n"
                + "         for(; _loc2_ < 3; _loc2_++)\n"
                + "         {\n"
                + "            var _loc3_:int = 0;\n"
                + "            while(_loc3_ < 3)\n"
                + "            {\n"
                + "               if(!param1)\n"
                + "               {\n"
                + "                  _loc3_++;\n"
                + "                  continue;\n"
                + "               }\n"
                + "               continue loop0;\n"
                + "            }\n"
                + "            trace(\"after inner\");\n"
                + "         }\n"
                + "         trace(\"done\");\n");
    }

    @Test
    public void testOuterContinuePrefixIsNotLoopBreak() throws Exception {
        decompilePCode("            pushbyte 0\n"
                + "            setlocal2\n"
                + "   outer_condition:\n"
                + "            getlocal2\n"
                + "            pushbyte 3\n"
                + "            ifge done\n"
                + "            pushbyte 0\n"
                + "            setlocal3\n"
                + "   inner_condition:\n"
                + "            getlocal3\n"
                + "            pushbyte 3\n"
                + "            ifge after_inner\n"
                + "            getlocal1\n"
                + "            iftrue before_outer_continue\n"
                + "            getlocal3\n"
                + "            pushbyte 1\n"
                + "            ifeq before_outer_continue\n"
                + "            inclocal_i 3\n"
                + "            jump inner_condition\n"
                + "   before_outer_continue:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"before outer continue\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump outer_continue\n"
                + "   after_inner:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"after inner\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   outer_continue:\n"
                + "            inclocal_i 2\n"
                + "            jump outer_condition\n"
                + "   done:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"done\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n",
                "         var _loc2_:int = 0;\n"
                + "         loop0:\n"
                + "         for(; _loc2_ < 3; _loc2_++)\n"
                + "         {\n"
                + "            var _loc3_:int = 0;\n"
                + "            while(_loc3_ < 3)\n"
                + "            {\n"
                + "               if(!param1)\n"
                + "               {\n"
                + "                  if(_loc3_ != 1)\n"
                + "                  {\n"
                + "                     _loc3_++;\n"
                + "                     continue;\n"
                + "                  }\n"
                + "               }\n"
                + "               trace(\"before outer continue\");\n"
                + "               continue loop0;\n"
                + "            }\n"
                + "            trace(\"after inner\");\n"
                + "         }\n"
                + "         trace(\"done\");\n");
    }

    @Test
    public void testWhileTrue() throws Exception {
        decompilePCode(
                "            getlex QName(PackageNamespace(\"\"),\"Math\")\n"
                + "            getlex QName(PackageNamespace(\"\"),\"Math\")\n"
                + "            debugline 8\n"
                + "            callproperty QName(PackageNamespace(\"\"),\"random\"), 0\n"
                + "            pushbyte 6\n"
                + "            multiply\n"
                + "            callproperty QName(PackageNamespace(\"\"),\"floor\"), 1\n"
                + "            convert_i\n"
                + "            setlocal1\n"
                + "            getlocal1\n"
                + "            debugline 10\n"
                + "            pushbyte 4\n"
                + "            ifngt ofs0034\n"
                + "            jump ofs0030\n"
                + "   ofs002d:\n"
                + "            label\n"
                + "            debugline 11\n"
                + "   ofs0030:\n"
                + "            jump ofs002d\n"
                + "   ofs0034:\n"
                + "            ", "         param1 = int(Math.floor(Math.random() * 6));\n"
                + "         if(param1 <= 4)\n"
                + "         {\n"
                + "            return;\n"
                + "         }\n"
                + "         while(true)\n"
                + "         {\n"
                + "         }\n");

    }

    @Test
    public void testSwitchCasesWithOnlyBreak() throws Exception {
        decompilePCode("jump checks\n"
                + "   caseA:\n"
                + "            label\n"
                + "            jump finish\n"
                + "   caseB:\n"
                + "            label\n"
                + "            jump finish\n"
                + "   caseDefault:\n"
                + "            label\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"play\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump finish\n"
                + "   checks:\n"
                + "            getlocal1\n"
                + "            setlocal2\n"
                + "            pushstring \"A\"\n"
                + "            getlocal2\n"
                + "            ifstrictne checkB\n"
                + "            pushbyte 0\n"
                + "            jump doSwitch\n"
                + "   checkB:\n"
                + "            pushstring \"B\"\n"
                + "            getlocal2\n"
                + "            ifstrictne noMatch\n"
                + "            pushbyte 1\n"
                + "            jump doSwitch\n"
                + "   noMatch:\n"
                + "            pushbyte 2\n"
                + "   doSwitch:\n"
                + "            kill 2\n"
                + "            lookupswitch caseDefault, [caseA, caseB, caseDefault]\n"
                + "   finish:\n",
                "         switch(param1)\n"
                + "         {\n"
                + "            default:\n"
                + "               trace(\"play\");\n"
                + "               break;\n"
                + "            case \"A\":\n"
                + "            case \"B\":\n"
                + "         }\n");
    }

    @Test
    public void testBreakAfterInnerLoopBreakBeforeIf() throws Exception {
        //the code after the middle loop is a precontinue of the outer loop,
        //it must stay the break target of the middle loop (no goto)
        decompilePCode("pushbyte 0\n"
                + "            setlocal 4\n"
                + "            jump iCond\n"
                + "   iBody:\n"
                + "            label\n"
                + "            pushbyte 0\n"
                + "            setlocal2\n"
                + "            jump jCond\n"
                + "   jBody:\n"
                + "            label\n"
                + "            getlocal1\n"
                + "            getlocal2\n"
                + "            getproperty MultinameL([PackageNamespace(\"\")])\n"
                + "            pushnull\n"
                + "            ifne jNext\n"
                + "            pushbyte 0\n"
                + "            setlocal3\n"
                + "            jump kCond\n"
                + "   kBody:\n"
                + "            label\n"
                + "            getlocal1\n"
                + "            getlocal3\n"
                + "            getproperty MultinameL([PackageNamespace(\"\")])\n"
                + "            pushnull\n"
                + "            ifeq kNext\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"found\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump afterK\n"
                + "   kNext:\n"
                + "            getlocal3\n"
                + "            increment_i\n"
                + "            setlocal3\n"
                + "   kCond:\n"
                + "            getlocal3\n"
                + "            pushbyte 10\n"
                + "            iflt kBody\n"
                + "   afterK:\n"
                + "            jump afterJ\n"
                + "   jNext:\n"
                + "            getlocal2\n"
                + "            increment_i\n"
                + "            setlocal2\n"
                + "   jCond:\n"
                + "            getlocal2\n"
                + "            pushbyte 10\n"
                + "            iflt jBody\n"
                + "   afterJ:\n"
                + "            getlocal2\n"
                + "            pushbyte 10\n"
                + "            ifnlt notFound\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"then\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            jump iNext\n"
                + "   notFound:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"else\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   iNext:\n"
                + "            getlocal 4\n"
                + "            increment_i\n"
                + "            setlocal 4\n"
                + "   iCond:\n"
                + "            getlocal 4\n"
                + "            pushbyte 10\n"
                + "            iflt iBody\n",
                "         var _loc4_:* = 0;\n"
                + "         while(_loc4_ < 10)\n"
                + "         {\n"
                + "            var _loc2_:* = 0;\n"
                + "            while(_loc2_ < 10)\n"
                + "            {\n"
                + "               if(param1[_loc2_] == null)\n"
                + "               {\n"
                + "                  var _loc3_:* = 0;\n"
                + "                  while(_loc3_ < 10)\n"
                + "                  {\n"
                + "                     if(param1[_loc3_] != null)\n"
                + "                     {\n"
                + "                        trace(\"found\");\n"
                + "                        break;\n"
                + "                     }\n"
                + "                     _loc3_++;\n"
                + "                  }\n"
                + "                  break;\n"
                + "               }\n"
                + "               _loc2_++;\n"
                + "            }\n"
                + "            if(_loc2_ < 10)\n"
                + "            {\n"
                + "               trace(\"then\");\n"
                + "            }\n"
                + "            else\n"
                + "            {\n"
                + "               trace(\"else\");\n"
                + "            }\n"
                + "            _loc4_++;\n"
                + "         }\n");
    }

    @Test
    public void testBreakFromLoopWithAndConditionToParentPreContinue() throws Exception {
        //the loop condition is an && (dup, iffalse, pop) and the code after the loop
        //is the precontinue of the parent for each: the jump to it from inside
        //the loop is a break, not a goto
        decompilePCode("pushbyte 0\n"
                + "            setlocal 5\n"
                + "            pushnull\n"
                + "            setlocal 6\n"
                + "            pushfalse\n"
                + "            setlocal 7\n"
                + "            getlocal0\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"flag\")\n"
                + "            iffalse start\n"
                + "            getlocal0\n"
                + "            pushfalse\n"
                + "            setproperty QName(PackageNamespace(\"\"),\"flag\")\n"
                + "            returnvoid\n"
                + "   start:\n"
                + "            pushbyte 0\n"
                + "            setlocal 10\n"
                + "            getlocal1\n"
                + "            coerce_a\n"
                + "            setlocal 11\n"
                + "            jump outerCond\n"
                + "   outerBody:\n"
                + "            label\n"
                + "            getlocal 11\n"
                + "            getlocal 10\n"
                + "            nextvalue\n"
                + "            setlocal 4\n"
                + "            pushbyte 0\n"
                + "            setlocal 5\n"
                + "            pushnull\n"
                + "            setlocal 6\n"
                + "            pushfalse\n"
                + "            setlocal 7\n"
                + "            jump l2Cond\n"
                + "   l2Body:\n"
                + "            label\n"
                + "            getlocal 4\n"
                + "            getlocal 6\n"
                + "            callproperty QName(PackageNamespace(\"\"),\"process2\"), 1\n"
                + "            convert_b\n"
                + "            setlocal 7\n"
                + "            getlocal 5\n"
                + "            increment_i\n"
                + "            dup\n"
                + "            convert_i\n"
                + "            setlocal 5\n"
                + "            pushbyte 5\n"
                + "            modulo\n"
                + "            pushbyte 0\n"
                + "            ifne l2Cond\n"
                + "            getlocal 4\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"slow\")\n"
                + "            dup\n"
                + "            iffalse l2And\n"
                + "            pop\n"
                + "            getlocal0\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"busy\")\n"
                + "            not\n"
                + "   l2And:\n"
                + "            iffalse l2Cond\n"
                + "            getlocal0\n"
                + "            pushtrue\n"
                + "            setproperty QName(PackageNamespace(\"\"),\"flag\")\n"
                + "            jump l2After\n"
                + "   l2Cond:\n"
                + "            getlocal0\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"flag\")\n"
                + "            not\n"
                + "            dup\n"
                + "            iffalse l2CondEnd\n"
                + "            pop\n"
                + "            getlocal 4\n"
                + "            callproperty QName(PackageNamespace(\"\"),\"next2\"), 0\n"
                + "            dup\n"
                + "            setlocal 12\n"
                + "            setlocal 6\n"
                + "            getlocal 12\n"
                + "            kill 12\n"
                + "            pushnull\n"
                + "            equals\n"
                + "            not\n"
                + "   l2CondEnd:\n"
                + "            iftrue l2Body\n"
                + "   l2After:\n"
                + "            getlocal0\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"flag\")\n"
                + "            iffalse outerCond\n"
                + "            returnvoid\n"
                + "   outerCond:\n"
                + "            hasnext2 11, 10\n"
                + "            iftrue outerBody\n"
                + "            kill 11\n"
                + "            kill 10\n",
                "         var _loc5_:int = 0;\n"
                + "         var _loc6_:* = null;\n"
                + "         var _loc7_:Boolean = false;\n"
                + "         if(this.flag)\n"
                + "         {\n"
                + "            this.flag = false;\n"
                + "            return;\n"
                + "         }\n"
                + "         for each(var _loc4_ in param1)\n"
                + "         {\n"
                + "            _loc5_ = 0;\n"
                + "            _loc6_ = null;\n"
                + "            _loc7_ = false;\n"
                + "            while(true)\n"
                + "            {\n"
                + "               _loc6_ = _loc4_.next2();\n"
                + "               if(!(!this.flag && _loc6_ != null))\n"
                + "               {\n"
                + "                  break;\n"
                + "               }\n"
                + "               _loc7_ = Boolean(_loc4_.process2(_loc6_));\n"
                + "               if(++_loc5_ % 5 == 0)\n"
                + "               {\n"
                + "                  if(_loc4_.slow && !this.busy)\n"
                + "                  {\n"
                + "                     this.flag = true;\n"
                + "                     break;\n"
                + "                  }\n"
                + "               }\n"
                + "            }\n"
                + "            if(this.flag)\n"
                + "            {\n"
                + "               return;\n"
                + "            }\n"
                + "         }\n");
    }

    @Test
    public void testLoopExitWithReturnAndOtherExit() throws Exception {
        //the return exit can be printed in place, the other exit is the break
        decompilePCode("getlocal1\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"done\")\n"
                + "            iftrue after\n"
                + "            jump head\n"
                + "   back:\n"
                + "            label\n"
                + "   head:\n"
                + "            getlocal1\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"available\")\n"
                + "            iftrue body\n"
                + "            returnvoid\n"
                + "   body:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"body\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "            getlocal1\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"more\")\n"
                + "            iftrue back\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"S\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   after:\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            pushstring \"after\"\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n",
                "         if(!param1.done)\n"
                + "         {\n"
                + "            do\n"
                + "            {\n"
                + "               if(!param1.available)\n"
                + "               {\n"
                + "                  return;\n"
                + "               }\n"
                + "               trace(\"body\");\n"
                + "            }\n"
                + "            while(param1.more);\n"
                + "            trace(\"S\");\n"
                + "         }\n"
                + "         trace(\"after\");\n");
    }

    @Test
    public void testObfuscatedSwitch() throws Exception {
        decompilePCode("getlocal0\n"
                + "            pushbyte 0\n"
                + "            initproperty QName(PackageNamespace(\"\"),\"testA\")\n"
                + "            jump ofs0012\n"
                + "            call 7\n"
                + "            throw\n"
                + "            callmethod 1413, 7\n"
                + "   ofs0012:\n"
                + "            jump ofs001a\n"
                + "            ifge ofs001a\n"
                + "   ofs001a:\n"
                + "            jump ofs0024\n"
                + "            equals\n"
                + "            pushwith\n"
                + "            newfunction 30\n"
                + "            pop\n"
                + "            setlocal2\n"
                + "   ofs0024:\n"
                + "            jump ofs0132\n"
                + "            pushwith\n"
                + "            setlocal3\n"
                + "   ofs002a:\n"
                + "            label\n"
                + "            pushtrue\n"
                + "            iftrue ofs0036\n"
                + "            returnvoid\n"
                + "            popscope\n"
                + "            callsuper QName(PackageNamespace(\"aaa\"),\"xxx\"), 10\n"
                + "            pushwith\n"
                + "   ofs0036:\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs01ff\n"
                + "            pushwith\n"
                + "            setlocal0\n"
                + "   ofs0040:\n"
                + "            label\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            getlocal0\n"
                + "            pushtrue\n"
                + "            iftrue ofs0052\n"
                + "            returnvoid\n"
                + "            popscope\n"
                + "            pushwith\n"
                + "            newfunction 48\n"
                + "            pop\n"
                + "            divide\n"
                + "   ofs0052:\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs01ff\n"
                + "            ifgt ofs0061\n"
                + "   ofs0061:\n"
                + "            label\n"
                + "            jump ofs006a\n"
                + "            ifstricteq ofs006a\n"
                + "   ofs006a:\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs0078\n"
                + "            popscope\n"
                + "            pushwith\n"
                + "            newfunction 4\n"
                + "            throw\n"
                + "            checkfilter\n"
                + "   ofs0078:\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs01ff\n"
                + "            pushwith\n"
                + "            setlocal0\n"
                + "            getlocal3\n"
                + "   ofs0083:\n"
                + "            label\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            pushfalse\n"
                + "            iffalse ofs0094\n"
                + "            returnvoid\n"
                + "            popscope\n"
                + "            equals\n"
                + "            setlocal1\n"
                + "            newactivation\n"
                + "            increment\n"
                + "            decrement\n"
                + "   ofs0094:\n"
                + "            jump ofs01ff\n"
                + "            pushwith\n"
                + "            setlocal2\n"
                + "   ofs009a:\n"
                + "            label\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            getlocal0\n"
                + "            jump ofs00a9\n"
                + "            modulo\n"
                + "            callsupervoid QName(PackageInternalNs(\"xxxx\"),\"tmp\"), 5\n"
                + "            pushwith\n"
                + "   ofs00a9:\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs00b4\n"
                + "            ifge ofs00b4\n"
                + "   ofs00b4:\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs00c1\n"
                + "            popscope\n"
                + "            callproplex QName(PackageNamespace(\"\"),\"xxx\"), 18\n"
                + "            setlocal2\n"
                + "   ofs00c1:\n"
                + "            jump ofs01ff\n"
                + "            ifstrictne ofs00c9\n"
                + "   ofs00c9:\n"
                + "            label\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs00d8\n"
                + "            nextname\n"
                + "            dxns \"position\"\n"
                + "            pushwith\n"
                + "            setlocal2\n"
                + "            returnvalue\n"
                + "   ofs00d8:\n"
                + "            jump ofs01ff\n"
                + "            pushwith\n"
                + "            setlocal3\n"
                + "            getlocal1\n"
                + "   ofs00df:\n"
                + "            label\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            getlocal0\n"
                + "            jump ofs00ef\n"
                + "            popscope\n"
                + "            pushwith\n"
                + "            newfunction 24\n"
                + "            pop\n"
                + "            setlocal3\n"
                + "   ofs00ef:\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs00fa\n"
                + "            ifne ofs00fa\n"
                + "   ofs00fa:\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            jump ofs0107\n"
                + "            popscope\n"
                + "            callproplex TypeName(QName(PackageNamespace(\"__AS3__.vec\"),\"Vector\")<QName(PackageNamespace(\"xxx\"),\"aaa\")>), 15\n"
                + "            throw\n"
                + "   ofs0107:\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            pushtrue\n"
                + "            iftrue ofs0117\n"
                + "            returnvoid\n"
                + "            checkfilter\n"
                + "            pushwith\n"
                + "            newfunction 51\n"
                + "            throw\n"
                + "            instanceof\n"
                + "   ofs0117:\n"
                + "            getlocal0\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"test\"), 0\n"
                + "            pushfalse\n"
                + "            iffalse ofs0127\n"
                + "            returnvoid\n"
                + "            popscope\n"
                + "            setlocal1\n"
                + "            istypelate\n"
                + "            setlocal1\n"
                + "            setlocal2\n"
                + "            divide\n"
                + "   ofs0127:\n"
                + "            jump ofs01ff\n"
                + "            pushwith\n"
                + "            setlocal2\n"
                + "            getlocal1\n"
                + "            jump ofs0132\n"
                + "   ofs0132:\n"
                + "            getlex QName(PackageNamespace(\"\"),\"Abc\")\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"value\")\n"
                + "            setlocal1\n"
                + "            jump ofs013f\n"
                + "            ifle ofs013f\n"
                + "   ofs013f:\n"
                + "            pushbyte 1\n"
                + "            getlocal1\n"
                + "            ifstricteq ofs014a\n"
                + "            jump ofs015e\n"
                + "   ofs014a:\n"
                + "            pushbyte 0\n"
                + "            jump ofs0156\n"
                + "            pushscope\n"
                + "            setlocal0\n"
                + "            istypelate\n"
                + "            nextvalue\n"
                + "            istypelate\n"
                + "            decrement\n"
                + "   ofs0156:\n"
                + "            jump ofs01e3\n"
                + "            ifgt ofs015e\n"
                + "   ofs015e:\n"
                + "            pushbyte 2\n"
                + "            getlocal1\n"
                + "            ifstricteq ofs0169\n"
                + "            jump ofs0178\n"
                + "   ofs0169:\n"
                + "            pushbyte 1\n"
                + "            jump ofs01e3\n"
                + "            jump ofs0173\n"
                + "   ofs0173:\n"
                + "            dup\n"
                + "            callsuper QName(PackageNamespace(\"xxx\"),\"aaa\"), 27\n"
                + "            pop\n"
                + "   ofs0178:\n"
                + "            pushbyte 3\n"
                + "            getlocal1\n"
                + "            ifstricteq ofs0183\n"
                + "            jump ofs018b\n"
                + "   ofs0183:\n"
                + "            pushbyte 2\n"
                + "            jump ofs01e3\n"
                + "            pushwith\n"
                + "            setlocal0\n"
                + "   ofs018b:\n"
                + "            pushbyte 4\n"
                + "            getlocal1\n"
                + "            ifstrictne ofs01a2\n"
                + "            pushbyte 3\n"
                + "            jump ofs01e3\n"
                + "            jump ofs019c\n"
                + "   ofs019c:\n"
                + "            convert_s\n"
                + "            construct 110\n"
                + "            pushwith\n"
                + "            nextvalue\n"
                + "            setlocal2\n"
                + "   ofs01a2:\n"
                + "            pushbyte 5\n"
                + "            getlocal1\n"
                + "            ifstricteq ofs01ad\n"
                + "            jump ofs01b5\n"
                + "   ofs01ad:\n"
                + "            pushbyte 4\n"
                + "            jump ofs01e3\n"
                + "            pushwith\n"
                + "            setlocal2\n"
                + "   ofs01b5:\n"
                + "            pushbyte 6\n"
                + "            jump ofs01bf\n"
                + "            ifgt ofs01bf\n"
                + "   ofs01bf:\n"
                + "            getlocal1\n"
                + "            ifstrictne ofs01d4\n"
                + "            pushbyte 5\n"
                + "            jump ofs01e3\n"
                + "            jump ofs01ce\n"
                + "   ofs01ce:\n"
                + "            popscope\n"
                + "            pushwith\n"
                + "            newfunction 49\n"
                + "            pop\n"
                + "            checkfilter\n"
                + "   ofs01d4:\n"
                + "            jump ofs01e1\n"
                + "            throw\n"
                + "            setlocal3\n"
                + "            getlocal2\n"
                + "            pushbyte 6\n"
                + "            jump ofs01e1\n"
                + "   ofs01e1:\n"
                + "            pushbyte 6\n"
                + "   ofs01e3:\n"
                + "            kill 1\n"
                + "            lookupswitch ofs00df, [ofs002a, ofs0040, ofs0061, ofs0083, ofs009a, ofs00c9, ofs00df]\n"
                + "   ofs01ff:\n"
                + "            getlocal0\n"
                + "            pushbyte 0\n"
                + "            initproperty QName(PackageNamespace(\"\"),\"testX\")\n"
                + "            jump ofs020e\n"
                + "            popscope\n"
                + "            pushwith\n"
                + "            setlocal1\n"
                + "            bitxor\n"
                + "            convert_d\n"
                + "            setlocal2\n"
                + "   ofs020e:\n"
                + "            pushfalse\n"
                + "            iffalse ofs021a\n"
                + "            returnvoid\n"
                + "            typeof\n"
                + "            checkfilter\n"
                + "            bitand\n"
                + "            in\n"
                + "            convert_s\n"
                + "            nextvalue\n"
                + "   ofs021a:\n"
                + "            jump ofs0222\n"
                + "            ifgt ofs0222\n"
                + "   ofs0222:\n"
                + "            pushtrue\n"
                + "            iftrue ofs022d\n"
                + "            returnvoid\n"
                + "            setlocal2\n"
                + "            callsuper QName(PackageNamespace(\"xxx\"),\"aaa\"), 15\n"
                + "            throw\n"
                + "   ofs022d:", "         this.testA = 0;\n"
                + "         switch(Abc.value)\n"
                + "         {\n"
                + "            case 1:\n"
                + "               this.test();\n"
                + "               break;\n"
                + "            case 2:\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               break;\n"
                + "            case 3:\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               break;\n"
                + "            case 4:\n"
                + "               this.test();\n"
                + "               break;\n"
                + "            case 5:\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               break;\n"
                + "            case 6:\n"
                + "               this.test();\n"
                + "               break;\n"
                + "            default:\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "               this.test();\n"
                + "         }\n"
                + "         this.testX = 0;\n");
    }

    @Test
    public void testStringSwitchCasesContinuingOuterLoop() throws Exception {
        String res = recompileSource("package { public class Test { public function testLoop() {"
                + "var result = 0;"
                + "while (true) {"
                + "switch (this.getValue()) {"
                + "case \"a\":"
                + "result = 1;"
                + "continue;"
                + "case \"b\":"
                + "result = 2;"
                + "continue;"
                + "case \"c\":"
                + "result = 3;"
                + "continue;"
                + "}"
                + "break;"
                + "}"
                + "return result;"
                + "} } }");
        Assert.assertTrue(res.contains("function testLoop()"), res);
        Assert.assertTrue(res.contains("case \"a\":"), res);
        Assert.assertTrue(res.contains("case \"b\":"), res);
        Assert.assertTrue(res.contains("case \"c\":"), res);
        Assert.assertFalse(res.contains("§§goto"), res);
    }

    // TODO: JPEXS @Test
    public void testNotRemoveParams() throws Exception {
        String res = recompile("function tst(p1,p2){"
                + "var a = 2;"
                + "var b = 3 * a;"
                + "if(b>1){"
                + "trace(\"OK1\");"
                + "}else{"
                + "trace(\"FAIL1\");"
                + "}"
                + "var c = p1*5;"
                + "if(c){"
                + "trace(\"OK2\");"
                + "}else{"
                + "trace(\"OK3\");"
                + "}"
                + "}");
        if (!res.contains("\"OK1\"")) {
            fail("basic if true onTrue removed");
        }
        if (res.contains("\"FAIL1\"")) {
            fail("basic if true onFalse not removed");
        }
        if (!res.contains("\"OK2\"")) {
            fail("if parameter onTrue removed");
        }
        if (!res.contains("\"OK3\"")) {
            fail("if parameter onFalse removed");
        }
    }

    //TODO: JPEXS @Test
    public void testEvalExpressionAfterWhile() throws Exception {
        String res = recompile("var a = 5;"
                + "while(true){"
                + "if(a==73){"
                + "a = 15;"
                + "}"
                + "if(a==1){"
                + "trace(\"FAIL1\");"
                + "}"
                + "if(a==5){"
                + "a=50;"
                + "}"
                + "if(a == 201){"
                + "break;"
                + "}"
                + "a++;"
                + "if(a == 53){"
                + "a = a + 20;"
                + "}"
                + "if(a>500){"
                + "trace(\"FAIL2\");"
                + "}"
                + "if(a==16){"
                + "a = 200;"
                + "}"
                + "}"
                + ""
                + "if(a == 201){"
                + "trace(\"OK\");"
                + "}else{"
                + "trace(\"FAIL3\");"
                + "}");
        if (res.contains("\"FAIL1\"")) {
            fail("unreachable if onTrue not removed");
        }
        if (res.contains("\"FAIL2\"")) {
            fail("unreachable if onTrue 2 not removed");
        }
        if (res.contains("\"FAIL3\"")) {
            fail("unreachable if onTrue 3 not removed");
        }
        if (!res.contains("\"OK\"")) {
            fail("reachable if onTrue removed");
        }
    }

    @Test
    public void testForFirstCommandDeclaration() throws Exception {
        //var i:int = param1.length; while (i--) trace(i);
        //the assignment and the condition are on the same line, so it is decompiled as for
        decompilePCodeWithLines("debug 1, \"i\", 1, 10\n"
                + "            debugline 10\n"
                + "            getlocal1\n"
                + "            getproperty QName(PackageNamespace(\"\"),\"length\")\n"
                + "            convert_i\n"
                + "            setlocal2\n"
                + "            jump cond\n"
                + "   body:\n"
                + "            label\n"
                + "            debugline 11\n"
                + "            findproperty QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            getlocal2\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "   cond:\n"
                + "            getlocal2\n"
                + "            dup\n"
                + "            decrement_i\n"
                + "            convert_i\n"
                + "            setlocal2\n"
                + "            convert_b\n"
                + "            iftrue body\n",
                "         for(var i:int = int(param1.length); Boolean(i--); )\n"
                + "         {\n"
                + "            trace(i);\n"
                + "         }\n");
    }

    @Test
    public void testRegExpLiteralIsNotComment() throws Exception {
        //as literals, new RegExp("") would be the comment // and new RegExp("*a")
        //the start of the comment /*; a trailing backslash would escape the closing /
        decompilePCode("findpropstrict QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            findpropstrict QName(PackageNamespace(\"\"),\"RegExp\")\n"
                + "            pushstring \"\"\n"
                + "            constructprop QName(PackageNamespace(\"\"),\"RegExp\"), 1\n"
                + "            findpropstrict QName(PackageNamespace(\"\"),\"RegExp\")\n"
                + "            pushstring \"*a\"\n"
                + "            constructprop QName(PackageNamespace(\"\"),\"RegExp\"), 1\n"
                + "            findpropstrict QName(PackageNamespace(\"\"),\"RegExp\")\n"
                + "            pushstring \"a\\\\\"\n"
                + "            constructprop QName(PackageNamespace(\"\"),\"RegExp\"), 1\n"
                + "            findpropstrict QName(PackageNamespace(\"\"),\"RegExp\")\n"
                + "            pushstring \"a+\"\n"
                + "            constructprop QName(PackageNamespace(\"\"),\"RegExp\"), 1\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 4\n",
                "         trace(new RegExp(\"\"),new RegExp(\"*a\"),new RegExp(\"a\\\\\"),/a+/);\n");
    }

    @Test
    public void testAttributeWithRuntimeName() throws Exception {
        //x..@[name] and x.@[name] lost the @ and became x..[name] and x[name]
        decompilePCode("findpropstrict QName(PackageNamespace(\"\"),\"trace\")\n"
                + "            getlocal1\n"
                + "            pushstring \"a\"\n"
                + "            getdescendants MultinameLA([PackageNamespace(\"\")])\n"
                + "            getlocal1\n"
                + "            pushstring \"b\"\n"
                + "            getproperty MultinameLA([PackageNamespace(\"\")])\n"
                + "            getlocal1\n"
                + "            pushstring \"c\"\n"
                + "            getproperty MultinameL([PackageNamespace(\"\")])\n"
                + "            callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 3\n",
                "         trace(param1..@[\"a\"],param1.@[\"b\"],param1[\"c\"]);\n");
    }

    @Test
    public void testSlotIncrementWithTempRegister() throws Exception {
        //ASC compiles i++ of a script variable (a slot of the global scope)
        //with a temporary register, killed at the end
        decompilePCodeWithLines("getglobalscope\n"
                + "            getslot 1\n"
                + "            increment_i\n"
                + "            setlocal2\n"
                + "            getlocal2\n"
                + "            getglobalscope\n"
                + "            swap\n"
                + "            setslot 1\n"
                + "            kill 2\n"
                + "            getglobalscope\n"
                + "            getslot 1\n"
                + "            decrement_i\n"
                + "            setlocal2\n"
                + "            getlocal2\n"
                + "            getglobalscope\n"
                + "            swap\n"
                + "            setslot 1\n"
                + "            kill 2\n",
                "         global.§§slot[1]++;\n"
                + "         global.§§slot[1]--;\n");
    }

    @Test
    public void testScriptInitializerImports() throws Exception {
        //top-level code after the package block, with no other trait outside
        //the package, lost the import of a name from another package
        String res = recompileSource("package { public class Test { } }\n"
                + "import flash.utils.getTimer;\n"
                + "getTimer();");
        res = res.replace("\r\n", "\n");
        int packageEnd = res.lastIndexOf("\n}\n");
        assertTrue(packageEnd != -1, res);
        String afterPackage = res.substring(packageEnd);
        assertTrue(afterPackage.contains("import flash.utils.getTimer;"), res);
        assertTrue(afterPackage.indexOf("import flash.utils.getTimer;") < afterPackage.indexOf("getTimer();"), res);
    }

    private String decompileScriptInitializer(String pCode) throws Exception {
        //a script whose initializer is the given p-code (after getlocal0, pushscope)
        final ABC abc = new ABC(new ABCContainerTag() {
            @Override
            public ABC getABC() {
                return null;
            }

            @Override
            public SWF getSwf() {
                return swf;
            }

            @Override
            public int compareTo(ABCContainerTag o) {
                return 0;
            }

            @Override
            public void setABC(ABC abc) {
            }
        });
        AbcIndexing index = swf.getAbcIndex();
        index.addAbc(abc);
        index.rebuildPkgToObjectsNameMap();
        ActionScript3Parser par = new ActionScript3Parser(index);
        par.addScript("trace(1);", "Test.as", 0, 0, swf.getDocumentClass(), abc);
        MethodBody b = abc.bodies.get(abc.findBodyIndex(abc.script_info.get(0).init_index));
        AVM2Code code = ASM3Parser.parse(abc, new StringReader("code\r\ngetlocal0\r\npushscope\r\n" + pCode + "returnvoid\r\n"), null, new MissingSymbolHandler() {
            @Override
            public boolean missingString(String value) {
                return true;
            }

            @Override
            public boolean missingInt(long value) {
                return true;
            }

            @Override
            public boolean missingUInt(long value) {
                return true;
            }

            @Override
            public boolean missingDouble(double value) {
                return true;
            }

            @Override
            public boolean missingDecimal(Decimal128 value) {
                return true;
            }

            @Override
            public boolean missingFloat(float value) {
                return true;
            }

            @Override
            public boolean missingFloat4(Float4 value) {
                return true;
            }
        }, b, new MethodInfo());
        b.setCode(code);
        b.max_regs = 4;
        b.max_stack = 10;
        HighlightedTextWriter writer = new HighlightedTextWriter(new CodeFormatting(), false);
        abc.script_info.get(0).getPacks(abc, 0, "", new ArrayList<>()).get(0).toSource(swf.getAbcIndex(), writer, abc.script_info.get(0).traits.traits, new ConvertData(), ScriptExportMode.AS, false, false, false);
        writer.finishHilights();
        return writer.toString().replace("\r\n", "\n");
    }

    @Test
    public void testScriptInitializerLocalRegisters() throws Exception {
        //top-level code that keeps an object in a local register and reads it
        //later lost the assignment: "new Object();" and "_loc1_.a = 1;"
        String res = decompileScriptInitializer("findpropstrict QName(PackageNamespace(\"\"),\"Object\")\r\n"
                + "constructprop QName(PackageNamespace(\"\"),\"Object\"), 0\r\n"
                + "setlocal1\r\n"
                + "getlocal1\r\n"
                + "pushbyte 1\r\n"
                + "setproperty QName(PackageNamespace(\"\"),\"a\")\r\n"
                + "findpropstrict QName(PackageNamespace(\"\"),\"trace\")\r\n"
                + "getlocal1\r\n"
                + "getproperty QName(PackageNamespace(\"\"),\"a\")\r\n"
                + "callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\r\n");
        assertTrue(res.contains("var _loc1_:Object = new Object();"), res);
        assertTrue(res.indexOf("var _loc1_:Object = new Object();") < res.indexOf("_loc1_.a = 1;"), res);
        assertTrue(res.contains("trace(_loc1_.a);"), res);
    }

    @Test
    public void testScriptInitializerUnreadLocalRegister() throws Exception {
        //a register that is only written is still eliminated
        String res = decompileScriptInitializer("findpropstrict QName(PackageNamespace(\"\"),\"Object\")\r\n"
                + "constructprop QName(PackageNamespace(\"\"),\"Object\"), 0\r\n"
                + "setlocal1\r\n");
        assertTrue(res.contains("new Object();"), res);
        assertTrue(!res.contains("_loc1_"), res);
    }

    @Test
    public void testCompoundScriptPackageSlotAssignments() throws Exception {
        //a script with several public variables: the top-level assignments
        //to them were removed and their declarations never written
        String res = recompileSource("package {\n"
                + "public var a:Object;\n"
                + "public var b:int;\n"
                + "public var c:Number;\n"
                + "public const d:int = 5;\n"
                + "}\n"
                + "a = new Object();\n"
                + "a.x = 1;\n"
                + "b = 2;\n"
                + "a = new Object();\n"
                + "trace(a, b);");
        res = res.replace("\r\n", "\n");
        assertTrue(res.contains("public var a:Object = new Object();"), res);
        assertTrue(res.contains("public var b:int = 2;"), res);
        assertTrue(res.indexOf("public var a:Object = new Object();") < res.indexOf("a.x = 1;"), res);
        assertTrue(res.indexOf("a.x = 1;") < res.indexOf("public var b:int = 2;"), res);
        assertTrue(res.indexOf("public var b:int = 2;") < res.lastIndexOf("a = new Object();"), res);
        assertTrue(res.contains("trace(a,b);"), res);
        assertTrue(res.contains("public var c:Number;"), res);
        assertTrue(res.contains("public const d:int = 5;"), res);
    }

    @Test
    public void testDoWhileWithBlockInPrecontinue() throws Exception {
        //an if between the continue target and the loop condition was written
        //inside the condition: while(if(b) { ... }, c). And the exit of the loop to the
        //end of the outer if had no jump, running the next iteration instead
        decompilePCode("pushbyte 0\n"
                + "setlocal1\n"
                + "pushbyte 5\n"
                + "setlocal2\n"
                + "getlocal2\n"
                + "pushbyte 0\n"
                + "ifeq exit\n"
                + "jump head\n"
                + "pc:\n"
                + "label\n"
                + "getlocal1\n"
                + "pushbyte 3\n"
                + "ifgt noIf\n"
                + "pushbyte 9\n"
                + "setlocal2\n"
                + "noIf:\n"
                + "label\n"
                + "getlocal1\n"
                + "pushbyte 1\n"
                + "add\n"
                + "convert_i\n"
                + "dup\n"
                + "setlocal1\n"
                + "pushbyte 10\n"
                + "ifeq after\n"
                + "head:\n"
                + "label\n"
                + "getlocal1\n"
                + "getlocal2\n"
                + "iflt pc\n"
                + "exit:\n"
                + "label\n"
                + "pushbyte 7\n"
                + "setlocal2\n"
                + "after:\n"
                + "label\n"
                + "pushbyte 8\n"
                + "setlocal2\n",
                "         param1 = 0;\n"
                + "         var _loc2_:int = 5;\n"
                + "         if(_loc2_ != 0)\n"
                + "         {\n"
                + "            do\n"
                + "            {\n"
                + "               if(param1 >= _loc2_)\n"
                + "               {\n"
                + "                  addr0030:\n"
                + "                  _loc2_ = 7;\n"
                + "                  break;\n"
                + "               }\n"
                + "               if(param1 <= 3)\n"
                + "               {\n"
                + "                  _loc2_ = 9;\n"
                + "               }\n"
                + "            }\n"
                + "            while(param1 += 1, param1 != 10);\n"
                + "            _loc2_ = 8;\n"
                + "            return;\n"
                + "         }\n"
                + "         §§goto(addr0030);\n");
    }

    @Test
    public void testCompoundScriptClassesAreNotInCode() throws Exception {
        //the classes of a script made by FlasCC, with dup: this.A = §§newclass(A,_temp_1) in the initializer
        String res = recompileSource("package {\n"
                + "public var a:int;\n"
                + "public class A {}\n"
                + "public class B {}\n"
                + "}\n"
                + "a = 1;\n"
                + "trace(a);",
                "code\n"
                + "getlocal0\n"
                + "pushscope\n"
                + "getscopeobject 0\n"
                + "getlex QName(PackageNamespace(\"\"),\"Object\")\n"
                + "dup\n"
                + "pushscope\n"
                + "newclass 0\n"
                + "popscope\n"
                + "initproperty QName(PackageNamespace(\"\"),\"A\")\n"
                + "findpropstrict QName(PackageNamespace(\"\"),\"trace\")\n"
                + "pushbyte 1\n"
                + "callpropvoid QName(PackageNamespace(\"\"),\"trace\"), 1\n"
                + "returnvoid\n");
        assertTrue(res.contains("trace(1);"), res);
        assertTrue(!res.contains("newclass"), res);
    }

    @Test
    public void testDeobfuscatorStopsAtTotalExecutionLimit() throws Exception {
        //each instruction starts a simulation of up to 30000 instructions: the
        //deobfuscator did not end in big methods compiled by FlasCC. It stops at a limit,
        //with the code still valid
        String pCode = "pushtrue\niffalse skip\npushbyte 1\npop\nskip:\nreturnvoid\n";
        String all = recompilePCode(pCode, new AVM2DeobfuscatorSimpleOld());
        assertTrue(!all.contains("iffalse"), all);
        String limited = recompilePCode(pCode, new AVM2DeobfuscatorSimpleOld() {
            {
                totalExecutionLimit = 0;
            }
        });
        assertTrue(limited.contains("iffalse"), limited);
    }

    @Test
    public void testSignExtendInstructionName() throws Exception {
        //sxi1, sxi8 and sxi16 were written as si1, si8 and si16, the names of the store
        //instructions (si8(value, address))
        decompilePCode("getlocal1\nsxi8\nsetlocal2\n"
                + "getlocal1\nsxi16\nsetlocal2\n"
                + "getlocal1\nsxi1\nsetlocal2\n",
                "         var _loc2_:int = sxi8(param1);\n"
                + "         _loc2_ = sxi16(param1);\n"
                + "         _loc2_ = sxi1(param1);\n");
    }

    @Test
    public void testGotoLabelLostInCopiedList() throws Exception {
        //the goto to code already written inside a loop added the label to the list where
        //that code was first written, which the loop handling had copied already: the goto
        //had no label. The control flow of a function compiled by FlasCC, with each block
        //replaced by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "iflt ofs00b1",
                "ofs007a:",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifgt ofs007a",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "iflt ofs00b1",
                "pushbyte 4",
                "setlocal 3",
                "jump ofs0120",
                "ofs00b1:",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifeq ofs00d7",
                "pushbyte 6",
                "setlocal 3",
                "jump ofs0161",
                "ofs00d7:",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifgt ofs0120",
                "pushbyte 8",
                "setlocal 3",
                "jump ofs0150",
                "ofs0120:",
                "pushbyte 9",
                "setlocal 3",
                "ofs0150:",
                "pushbyte 10",
                "setlocal 3",
                "ofs0161:",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "iflt ofs01f2",
                "ofs0186:",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifne ofs0186",
                "pushbyte 13",
                "setlocal 3",
                "jump ofs01ff",
                "ofs01f2:",
                "pushbyte 14",
                "setlocal 3",
                "ofs01ff:",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifge ofs026e",
                "pushbyte 16",
                "setlocal 3",
                "ofs0216:",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifne ofs0216",
                "ofs026e:",
                "pushbyte 18",
                "setlocal 3",
                "returnvoid"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("§§goto\\((addr[0-9a-f]+)\\)").matcher(res);
        boolean found = false;
        while (m.find()) {
            found = true;
            assertTrue(res.contains(m.group(1) + ":"), res);
        }
        assertTrue(found, res);
    }

    @Test
    public void testLoopReopenedForBlockIsClosed() throws Exception {
        //a block inside a loop (a labeled break region) handles the loop first and opens
        //it again for the code of the block, but nothing closed it afterwards: the code
        //at the break of the loop (the end of the function, "_loc3_ = 48") became a
        //"break loop1" to a loop that is not there. The control flow of a function
        //compiled by FlasCC, with each block replaced by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifgt ofs0071",
                "pushbyte 2",
                "setlocal 3",
                "ofs0071:",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifgt ofs0093",
                "pushbyte 4",
                "setlocal 3",
                "jump ofs0162",
                "ofs0093:",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "iflt ofs0162",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifne ofs0110",
                "pushbyte 7",
                "setlocal 3",
                "jump ofs0162",
                "ofs0110:",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifne ofs05ab",
                "ofs0162:",
                "pushbyte 9",
                "setlocal 3",
                "jump ofs0254",
                "ofs0177:",
                "pushbyte 10",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifgt ofs01a7",
                "pushbyte 11",
                "setlocal 3",
                "ofs01a7:",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "iflt ofs024b",
                "pushbyte 13",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifne ofs0203",
                "pushbyte 14",
                "setlocal 3",
                "jump ofs024b",
                "ofs0203:",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifne ofs05ab",
                "ofs024b:",
                "pushbyte 16",
                "setlocal 3",
                "ofs0254:",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifgt ofs0177",
                "pushbyte 18",
                "setlocal 3",
                "jump ofs0576",
                "ofs026e:",
                "pushbyte 19",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "iflt ofs028b",
                "ofs0277:",
                "pushbyte 20",
                "setlocal 3",
                "jump ofs02b1",
                "ofs028b:",
                "pushbyte 21",
                "setlocal 3",
                "ofs02b1:",
                "pushbyte 22",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "iflt ofs038a",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifne ofs033f",
                "pushbyte 24",
                "setlocal 3",
                "jump ofs038a",
                "ofs033f:",
                "pushbyte 25",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifne ofs05ab",
                "ofs038a:",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "ifgt ofs03ae",
                "pushbyte 27",
                "setlocal 3",
                "ofs03ae:",
                "pushbyte 28",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "iflt ofs0479",
                "pushbyte 29",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "iflt ofs0479",
                "pushbyte 30",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifne ofs042a",
                "pushbyte 31",
                "setlocal 3",
                "jump ofs0479",
                "ofs042a:",
                "pushbyte 32",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "ifne ofs05ab",
                "ofs0479:",
                "pushbyte 33",
                "setlocal 3",
                "jump ofs055a",
                "ofs0486:",
                "pushbyte 34",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "ifgt ofs04a7",
                "pushbyte 35",
                "setlocal 3",
                "ofs04a7:",
                "pushbyte 36",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "iflt ofs0551",
                "pushbyte 37",
                "setlocal 3",
                "getlocal1",
                "pushbyte 22",
                "ifne ofs0506",
                "pushbyte 38",
                "setlocal 3",
                "jump ofs0551",
                "ofs0506:",
                "pushbyte 39",
                "setlocal 3",
                "getlocal1",
                "pushbyte 23",
                "ifne ofs05ab",
                "ofs0551:",
                "pushbyte 40",
                "setlocal 3",
                "ofs055a:",
                "pushbyte 41",
                "setlocal 3",
                "getlocal1",
                "pushbyte 24",
                "ifgt ofs0486",
                "pushbyte 42",
                "setlocal 3",
                "ofs0576:",
                "pushbyte 43",
                "setlocal 3",
                "getlocal1",
                "pushbyte 25",
                "ifgt ofs026e",
                "pushbyte 44",
                "setlocal 3",
                "getlocal1",
                "pushbyte 26",
                "ifgt ofs0277",
                "pushbyte 45",
                "setlocal 3",
                "getlocal1",
                "pushbyte 27",
                "ifgt ofs05a4",
                "pushbyte 46",
                "setlocal 3",
                "ofs05a4:",
                "pushbyte 47",
                "setlocal 3",
                "ofs05ab:",
                "pushbyte 48",
                "setlocal 3",
                "returnvoid"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertTrue(res.trim().endsWith("_loc3_ = 48;"), res);
        assertTrue(!res.contains("break loop1"), res);
    }

    /**
     * Every break or continue to a loop label has the label, and every goto has its label.
     */
    private void assertJumpsHaveTargets(String res) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:break|continue) (loop[0-9]+);").matcher(res);
        while (m.find()) {
            assertTrue(res.contains(m.group(1) + ":"), res);
        }
        m = java.util.regex.Pattern.compile("§§goto\\((addr[0-9a-f]+)\\)").matcher(res);
        while (m.find()) {
            assertTrue(res.contains(m.group(1) + ":"), res);
        }
    }

    @Test
    public void testBreakToLoopNotInResultBecomesGoto() throws Exception {
        //a break to a loop that does not enclose it in the result (the loop was not
        //written there) had no label: it becomes a goto to the code at the break of
        //the loop. The control flow of a function compiled by FlasCC, with each block
        //replaced by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "jump ofs03a4",
                "ofs004c:",
                "ofs004d:",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifne ofs0338",
                "pushbyte 3",
                "setlocal 3",
                "ofs00c8:",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifne ofs0322",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifeq ofs02f0",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifeq ofs0228",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "iflt ofs015d",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifle ofs015d",
                "pushbyte 9",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifne ofs0228",
                "ofs015d:",
                "pushbyte 10",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifeq ofs01d4",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "iflt ofs01d4",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifle ofs01d4",
                "pushbyte 13",
                "setlocal 3",
                "jump ofs0228",
                "ofs01d4:",
                "pushbyte 14",
                "setlocal 3",
                "ofs01f5:",
                "pushbyte 15",
                "setlocal 3",
                "ofs0228:",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifne ofs02f0",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifeq ofs02f0",
                "pushbyte 18",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifne ofs02f0",
                "pushbyte 19",
                "setlocal 3",
                "ofs02f0:",
                "pushbyte 20",
                "setlocal 3",
                "returnvoid",
                "ofs0322:",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifeq ofs01f5",
                "ofs0333:",
                "jump ofs0333",
                "ofs0338:",
                "pushbyte 22",
                "setlocal 3",
                "getlocal1",
                "lookupswitch ofs0347, [ofs034b]",
                "ofs0347:",
                "jump ofs034f",
                "ofs034b:",
                "jump ofs00c8",
                "ofs034f:",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "throw",
                "ofs0355:",
                "ofs0356:",
                "pushbyte 24",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "ifnge ofs0376",
                "pushbyte 25",
                "setlocal 3",
                "getlocal1",
                "throw",
                "ofs0376:",
                "pushbyte 26",
                "setlocal 3",
                "ofs03a4:",
                "jump ofs004c"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertJumpsHaveTargets(res);
    }

    @Test
    public void testIfWithGotosKeepsLabeledCodeAfterIt() throws Exception {
        //the code of a label moved after an if whose branches both end with a goto (processIfGotos2)
        //was removed as not reached, though the goto jumps there: half of it, as the loop
        //removing it skipped items. The control flow of a function compiled by FlasCC, with each
        //block replaced by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "ofs0084:",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifeq ofs00dd",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifeq ofs00a4",
                "pushbyte 4",
                "setlocal 3",
                "jump ofs0084",
                "ofs00a4:",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifeq ofs00dd",
                "pushbyte 6",
                "setlocal 3",
                "jump ofs0dae",
                "ofs00dd:",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifeq ofs02c8",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifeq ofs02c8",
                "pushbyte 9",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifeq ofs0184",
                "pushbyte 10",
                "setlocal 3",
                "jump ofs0173",
                "ofs015d:",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifeq ofs0249",
                "pushbyte 12",
                "setlocal 3",
                "ofs0173:",
                "pushbyte 13",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "iflt ofs015d",
                "ofs0184:",
                "pushbyte 14",
                "setlocal 3",
                "ofs018d:",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifeq ofs023c",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifne ofs01d3",
                "pushbyte 17",
                "setlocal 3",
                "jump ofs0249",
                "ofs01d3:",
                "pushbyte 18",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifne ofs01f6",
                "pushbyte 19",
                "setlocal 3",
                "jump ofs0249",
                "ofs01f6:",
                "pushbyte 20",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifne ofs0219",
                "pushbyte 21",
                "setlocal 3",
                "jump ofs0249",
                "ofs0219:",
                "pushbyte 22",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifne ofs023c",
                "pushbyte 23",
                "setlocal 3",
                "jump ofs0249",
                "ofs023c:",
                "pushbyte 24",
                "setlocal 3",
                "jump ofs018d",
                "ofs0249:",
                "pushbyte 25",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifgt ofs02c8",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "ifne ofs028c",
                "pushbyte 27",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "ifeq ofs02c8",
                "pushbyte 28",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "ifne ofs028c",
                "pushbyte 29",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifeq ofs02c8",
                "ofs028c:",
                "pushbyte 30",
                "setlocal 3",
                "ofs0294:",
                "pushbyte 31",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "ifeq ofs02cf",
                "pushbyte 32",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "ifeq ofs02bf",
                "pushbyte 33",
                "setlocal 3",
                "jump ofs0294",
                "ofs02bf:",
                "pushbyte 34",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "ifeq ofs02cf",
                "ofs02c8:",
                "pushbyte 35",
                "setlocal 3",
                "ofs02cf:",
                "pushbyte 36",
                "setlocal 3",
                "getlocal1",
                "pushbyte 22",
                "ifeq ofs0336",
                "pushbyte 37",
                "setlocal 3",
                "jump ofs0325",
                "ofs030f:",
                "pushbyte 38",
                "setlocal 3",
                "getlocal1",
                "pushbyte 23",
                "ifeq ofs03fb",
                "pushbyte 39",
                "setlocal 3",
                "ofs0325:",
                "pushbyte 40",
                "setlocal 3",
                "getlocal1",
                "pushbyte 24",
                "iflt ofs030f",
                "ofs0336:",
                "pushbyte 41",
                "setlocal 3",
                "ofs033f:",
                "pushbyte 42",
                "setlocal 3",
                "getlocal1",
                "pushbyte 25",
                "ifeq ofs03ee",
                "pushbyte 43",
                "setlocal 3",
                "getlocal1",
                "pushbyte 26",
                "ifne ofs0385",
                "pushbyte 44",
                "setlocal 3",
                "jump ofs03fb",
                "ofs0385:",
                "pushbyte 45",
                "setlocal 3",
                "getlocal1",
                "pushbyte 27",
                "ifne ofs03a8",
                "pushbyte 46",
                "setlocal 3",
                "jump ofs03fb",
                "ofs03a8:",
                "pushbyte 47",
                "setlocal 3",
                "getlocal1",
                "pushbyte 28",
                "ifne ofs03cb",
                "pushbyte 48",
                "setlocal 3",
                "jump ofs03fb",
                "ofs03cb:",
                "pushbyte 49",
                "setlocal 3",
                "getlocal1",
                "pushbyte 29",
                "ifne ofs03ee",
                "pushbyte 50",
                "setlocal 3",
                "jump ofs03fb",
                "ofs03ee:",
                "pushbyte 51",
                "setlocal 3",
                "jump ofs033f",
                "ofs03fb:",
                "pushbyte 52",
                "setlocal 3",
                "getlocal1",
                "pushbyte 30",
                "ifeq ofs0dae",
                "pushbyte 53",
                "setlocal 3",
                "ofs046d:",
                "pushbyte 54",
                "setlocal 3",
                "getlocal1",
                "pushbyte 31",
                "ifeq ofs04ac",
                "pushbyte 55",
                "setlocal 3",
                "getlocal1",
                "pushbyte 32",
                "ifeq ofs0495",
                "pushbyte 56",
                "setlocal 3",
                "jump ofs046d",
                "ofs0495:",
                "pushbyte 57",
                "setlocal 3",
                "getlocal1",
                "pushbyte 33",
                "ifeq ofs04ac",
                "pushbyte 58",
                "setlocal 3",
                "ofs04ac:",
                "pushbyte 59",
                "setlocal 3",
                "getlocal1",
                "pushbyte 34",
                "ifeq ofs0500",
                "pushbyte 60",
                "setlocal 3",
                "getlocal1",
                "pushbyte 35",
                "ifeq ofs04db",
                "pushbyte 61",
                "setlocal 3",
                "jump ofs04ac",
                "ofs04db:",
                "pushbyte 62",
                "setlocal 3",
                "getlocal1",
                "pushbyte 36",
                "ifeq ofs0500",
                "pushbyte 63",
                "setlocal 3",
                "ofs0500:",
                "ofs0501:",
                "pushbyte 64",
                "setlocal 3",
                "getlocal1",
                "pushbyte 37",
                "ifeq ofs054a",
                "pushbyte 65",
                "setlocal 3",
                "getlocal1",
                "pushbyte 38",
                "ifeq ofs052c",
                "pushbyte 66",
                "setlocal 3",
                "jump ofs0501",
                "ofs052c:",
                "pushbyte 67",
                "setlocal 3",
                "getlocal1",
                "pushbyte 39",
                "ifeq ofs054a",
                "pushbyte 68",
                "setlocal 3",
                "ofs054a:",
                "pushbyte 69",
                "setlocal 3",
                "jump ofs055c",
                "ofs0553:",
                "pushbyte 70",
                "setlocal 3",
                "ofs055c:",
                "pushbyte 71",
                "setlocal 3",
                "getlocal1",
                "pushbyte 40",
                "ifeq ofs0585",
                "pushbyte 72",
                "setlocal 3",
                "getlocal1",
                "pushbyte 41",
                "ifne ofs0553",
                "pushbyte 73",
                "setlocal 3",
                "getlocal1",
                "pushbyte 42",
                "ifne ofs0595",
                "ofs0585:",
                "pushbyte 74",
                "setlocal 3",
                "jump ofs069b",
                "ofs0595:",
                "pushbyte 75",
                "setlocal 3",
                "getlocal1",
                "pushbyte 43",
                "ifeq ofs069b",
                "pushbyte 76",
                "setlocal 3",
                "getlocal1",
                "pushbyte 44",
                "ifeq ofs069b",
                "pushbyte 77",
                "setlocal 3",
                "getlocal1",
                "pushbyte 45",
                "ifne ofs05d0",
                "pushbyte 78",
                "setlocal 3",
                "jump ofs05dd",
                "ofs05d0:",
                "pushbyte 79",
                "setlocal 3",
                "jump ofs068e",
                "ofs05dd:",
                "pushbyte 80",
                "setlocal 3",
                "getlocal1",
                "pushbyte 46",
                "ifeq ofs069b",
                "jump ofs0600",
                "ofs05ee:",
                "pushbyte 81",
                "setlocal 3",
                "jump ofs05dd",
                "ofs0600:",
                "pushbyte 82",
                "setlocal 3",
                "getlocal1",
                "pushbyte 47",
                "ifeq ofs066f",
                "jump ofs05ee",
                "ofs0619:",
                "pushbyte 83",
                "setlocal 3",
                "getlocal1",
                "pushbyte 48",
                "ifne ofs067c",
                "pushbyte 84",
                "setlocal 3",
                "getlocal1",
                "pushbyte 49",
                "ifeq ofs065a",
                "pushbyte 85",
                "setlocal 3",
                "getlocal1",
                "pushbyte 50",
                "ifne ofs0619",
                "ofs065a:",
                "pushbyte 86",
                "setlocal 3",
                "getlocal1",
                "pushbyte 51",
                "ifne ofs067c",
                "ofs066f:",
                "pushbyte 87",
                "setlocal 3",
                "jump ofs069b",
                "ofs067c:",
                "pushbyte 88",
                "setlocal 3",
                "ofs068e:",
                "pushbyte 89",
                "setlocal 3",
                "getlocal1",
                "pushbyte 52",
                "ifne ofs0619",
                "ofs069b:",
                "pushbyte 90",
                "setlocal 3",
                "getlocal1",
                "pushbyte 53",
                "ifeq ofs06b3",
                "pushbyte 91",
                "setlocal 3",
                "ofs06b3:",
                "pushbyte 92",
                "setlocal 3",
                "getlocal1",
                "pushbyte 54",
                "ifeq ofs0718",
                "pushbyte 93",
                "setlocal 3",
                "jump ofs0706",
                "ofs06f0:",
                "pushbyte 94",
                "setlocal 3",
                "getlocal1",
                "pushbyte 55",
                "ifeq ofs07e1",
                "pushbyte 95",
                "setlocal 3",
                "ofs0706:",
                "pushbyte 96",
                "setlocal 3",
                "getlocal1",
                "pushbyte 56",
                "iflt ofs06f0",
                "ofs0718:",
                "pushbyte 97",
                "setlocal 3",
                "ofs0721:",
                "pushbyte 98",
                "setlocal 3",
                "getlocal1",
                "pushbyte 57",
                "ifeq ofs07d4",
                "pushbyte 99",
                "setlocal 3",
                "getlocal1",
                "pushbyte 58",
                "ifne ofs0768",
                "pushbyte 0",
                "setlocal 3",
                "jump ofs07e1",
                "ofs0768:",
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 59",
                "ifne ofs078c",
                "pushbyte 2",
                "setlocal 3",
                "jump ofs07e1",
                "ofs078c:",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 60",
                "ifne ofs07b0",
                "pushbyte 4",
                "setlocal 3",
                "jump ofs07e1",
                "ofs07b0:",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 61",
                "ifne ofs07d4",
                "pushbyte 6",
                "setlocal 3",
                "jump ofs07e1",
                "ofs07d4:",
                "pushbyte 7",
                "setlocal 3",
                "jump ofs0721",
                "ofs07e1:",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 62",
                "ifeq ofs0865",
                "pushbyte 9",
                "setlocal 3",
                "jump ofs0ce0",
                "ofs0865:",
                "pushbyte 10",
                "setlocal 3",
                "jump ofs0dae",
                "ofs089b:",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 63",
                "ifeq ofs0cc3",
                "jump ofs0be6",
                "ofs08b1:",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 64",
                "ifgt ofs08ea",
                "pushbyte 13",
                "setlocal 3",
                "getlocal1",
                "pushbyte 65",
                "ifeq ofs092f",
                "pushbyte 14",
                "setlocal 3",
                "getlocal1",
                "pushbyte 66",
                "ifeq ofs091b",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 67",
                "ifeq ofs0923",
                "jump ofs0934",
                "ofs08ea:",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 68",
                "ifeq ofs0912",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 69",
                "ifeq ofs0969",
                "pushbyte 18",
                "setlocal 3",
                "getlocal1",
                "pushbyte 70",
                "ifne ofs0934",
                "pushbyte 19",
                "setlocal 3",
                "jump ofs0969",
                "ofs0912:",
                "pushbyte 20",
                "setlocal 3",
                "jump ofs0969",
                "ofs091b:",
                "pushbyte 21",
                "setlocal 3",
                "jump ofs0969",
                "ofs0923:",
                "pushbyte 22",
                "setlocal 3",
                "jump ofs0969",
                "ofs092f:",
                "pushbyte 23",
                "setlocal 3",
                "ofs0934:",
                "pushbyte 24",
                "setlocal 3",
                "getlocal1",
                "pushbyte 71",
                "ifgt ofs0a1b",
                "pushbyte 25",
                "setlocal 3",
                "jump ofs0bd5",
                "ofs0969:",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 72",
                "ifeq ofs09e8",
                "pushbyte 27",
                "setlocal 3",
                "jump ofs09c9",
                "ofs09a2:",
                "pushbyte 28",
                "setlocal 3",
                "getlocal1",
                "pushbyte 73",
                "ifeq ofs0a02",
                "ofs09c9:",
                "pushbyte 29",
                "setlocal 3",
                "getlocal1",
                "pushbyte 74",
                "ifne ofs09a2",
                "pushbyte 30",
                "setlocal 3",
                "getlocal1",
                "pushbyte 75",
                "ifeq ofs09e8",
                "pushbyte 31",
                "setlocal 3",
                "ofs09e8:",
                "pushbyte 32",
                "setlocal 3",
                "getlocal1",
                "pushbyte 76",
                "ifne ofs09e8",
                "ofs0a02:",
                "pushbyte 33",
                "setlocal 3",
                "getlocal1",
                "pushbyte 77",
                "iflt ofs0a71",
                "ofs0a1b:",
                "pushbyte 34",
                "setlocal 3",
                "jump ofs0dae",
                "ofs0a71:",
                "pushbyte 35",
                "setlocal 3",
                "getlocal1",
                "pushbyte 78",
                "ifeq ofs0ad5",
                "pushbyte 36",
                "setlocal 3",
                "jump ofs0ac4",
                "ofs0aaf:",
                "pushbyte 37",
                "setlocal 3",
                "getlocal1",
                "pushbyte 79",
                "ifeq ofs0b9e",
                "pushbyte 38",
                "setlocal 3",
                "ofs0ac4:",
                "pushbyte 39",
                "setlocal 3",
                "getlocal1",
                "pushbyte 80",
                "iflt ofs0aaf",
                "ofs0ad5:",
                "pushbyte 40",
                "setlocal 3",
                "ofs0ade:",
                "pushbyte 41",
                "setlocal 3",
                "getlocal1",
                "pushbyte 81",
                "ifeq ofs0b91",
                "pushbyte 42",
                "setlocal 3",
                "getlocal1",
                "pushbyte 82",
                "ifne ofs0b25",
                "pushbyte 43",
                "setlocal 3",
                "jump ofs0b9e",
                "ofs0b25:",
                "pushbyte 44",
                "setlocal 3",
                "getlocal1",
                "pushbyte 83",
                "ifne ofs0b49",
                "pushbyte 45",
                "setlocal 3",
                "jump ofs0b9e",
                "ofs0b49:",
                "pushbyte 46",
                "setlocal 3",
                "getlocal1",
                "pushbyte 84",
                "ifne ofs0b6d",
                "pushbyte 47",
                "setlocal 3",
                "jump ofs0b9e",
                "ofs0b6d:",
                "pushbyte 48",
                "setlocal 3",
                "getlocal1",
                "pushbyte 85",
                "ifne ofs0b91",
                "pushbyte 49",
                "setlocal 3",
                "jump ofs0b9e",
                "ofs0b91:",
                "pushbyte 50",
                "setlocal 3",
                "jump ofs0ade",
                "ofs0b9e:",
                "pushbyte 51",
                "setlocal 3",
                "jump ofs0bd5",
                "ofs0bab:",
                "pushbyte 52",
                "setlocal 3",
                "getlocal1",
                "pushbyte 86",
                "ifgt ofs0a1b",
                "pushbyte 53",
                "setlocal 3",
                "ofs0bd5:",
                "pushbyte 54",
                "setlocal 3",
                "ofs0be6:",
                "pushbyte 55",
                "setlocal 3",
                "getlocal1",
                "pushbyte 87",
                "ifeq ofs08b1",
                "pushbyte 56",
                "setlocal 3",
                "getlocal1",
                "pushbyte 88",
                "ifne ofs0bab",
                "pushbyte 57",
                "setlocal 3",
                "getlocal1",
                "pushbyte 89",
                "ifne ofs0ce0",
                "pushbyte 58",
                "setlocal 3",
                "jump ofs0dae",
                "ofs0cc3:",
                "pushbyte 59",
                "setlocal 3",
                "jump ofs0969",
                "ofs0ce0:",
                "pushbyte 60",
                "setlocal 3",
                "getlocal1",
                "pushbyte 90",
                "ifeq ofs0d5c",
                "pushbyte 61",
                "setlocal 3",
                "jump ofs0cff",
                "ofs0cf6:",
                "pushbyte 62",
                "setlocal 3",
                "ofs0cff:",
                "pushbyte 63",
                "setlocal 3",
                "ofs0d0a:",
                "pushbyte 64",
                "setlocal 3",
                "getlocal1",
                "pushbyte 91",
                "ifne ofs0d48",
                "pushbyte 65",
                "setlocal 3",
                "getlocal1",
                "pushbyte 92",
                "ifeq ofs089b",
                "pushbyte 66",
                "setlocal 3",
                "jump ofs089b",
                "ofs0d48:",
                "pushbyte 67",
                "setlocal 3",
                "getlocal1",
                "pushbyte 93",
                "ifeq ofs0cf6",
                "jump ofs0d0a",
                "ofs0d5c:",
                "pushbyte 68",
                "setlocal 3",
                "ofs0dae:",
                "pushbyte 69",
                "setlocal 3",
                "getlocal1",
                "pushbyte 94",
                "iflt ofs0f87",
                "pushbyte 70",
                "setlocal 3",
                "ofs0e2f:",
                "pushbyte 71",
                "setlocal 3",
                "getlocal1",
                "pushbyte 95",
                "ifgt ofs0e2f",
                "pushbyte 72",
                "setlocal 3",
                "getlocal1",
                "pushbyte 96",
                "ifgt ofs0e80",
                "pushbyte 73",
                "setlocal 3",
                "ofs0e80:",
                "pushbyte 74",
                "setlocal 3",
                "getlocal1",
                "pushbyte 97",
                "ifeq ofs0eeb",
                "pushbyte 75",
                "setlocal 3",
                "jump ofs0ed3",
                "ofs0ea9:",
                "pushbyte 76",
                "setlocal 3",
                "getlocal1",
                "pushbyte 98",
                "ifeq ofs0f1a",
                "ofs0ed3:",
                "pushbyte 77",
                "setlocal 3",
                "getlocal1",
                "pushbyte 99",
                "ifne ofs0ea9",
                "pushbyte 78",
                "setlocal 3",
                "getlocal1",
                "pushbyte 100",
                "ifeq ofs0eeb",
                "pushbyte 79",
                "setlocal 3",
                "ofs0eeb:",
                "pushbyte 80",
                "setlocal 3",
                "getlocal1",
                "pushbyte 101",
                "ifeq ofs0f1a",
                "jump ofs0eeb",
                "ofs0f09:",
                "pushbyte 81",
                "setlocal 3",
                "ofs0f1a:",
                "pushbyte 82",
                "setlocal 3",
                "getlocal1",
                "pushbyte 102",
                "ifeq ofs106b",
                "pushbyte 83",
                "setlocal 3",
                "getlocal1",
                "pushbyte 103",
                "ifne ofs0f09",
                "pushbyte 84",
                "setlocal 3",
                "ofs0f3b:",
                "pushbyte 85",
                "setlocal 3",
                "getlocal1",
                "pushbyte 104",
                "ifeq ofs0f5e",
                "pushbyte 86",
                "setlocal 3",
                "ofs0f5e:",
                "pushbyte 87",
                "setlocal 3",
                "getlocal1",
                "pushbyte 105",
                "ifne ofs0f3b",
                "pushbyte 88",
                "setlocal 3",
                "jump ofs106b",
                "ofs0f87:",
                "pushbyte 89",
                "setlocal 3",
                "getlocal1",
                "pushbyte 106",
                "ifeq ofs1031",
                "pushbyte 90",
                "setlocal 3",
                "jump ofs1013",
                "ofs0fec:",
                "pushbyte 91",
                "setlocal 3",
                "getlocal1",
                "pushbyte 107",
                "ifeq ofs1049",
                "ofs1013:",
                "pushbyte 92",
                "setlocal 3",
                "getlocal1",
                "pushbyte 108",
                "ifne ofs0fec",
                "pushbyte 93",
                "setlocal 3",
                "getlocal1",
                "pushbyte 109",
                "ifeq ofs1031",
                "pushbyte 94",
                "setlocal 3",
                "ofs1031:",
                "pushbyte 95",
                "setlocal 3",
                "getlocal1",
                "pushbyte 110",
                "ifne ofs1031",
                "ofs1049:",
                "pushbyte 96",
                "setlocal 3",
                "getlocal1",
                "pushbyte 111",
                "iflt ofs106b",
                "pushbyte 97",
                "setlocal 3",
                "ofs106b:",
                "pushbyte 98",
                "setlocal 3",
                "getlocal1",
                "pushbyte 112",
                "iflt ofs1088",
                "pushbyte 99",
                "setlocal 3",
                "jump ofs10e4",
                "ofs1088:",
                "pushbyte 0",
                "setlocal 3",
                "getlocal1",
                "pushbyte 113",
                "ifeq ofs10e4",
                "pushbyte 1",
                "setlocal 3",
                "ofs10e4:",
                "pushbyte 2",
                "setlocal 3",
                "returnvoid"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertJumpsHaveTargets(res);
    }

    @Test
    public void testGotoToPartWrittenInDiscardedPrecontinue() throws Exception {
        //the precontinue of a loop is written apart and thrown away when it has statements,
        //but the lists where its parts were written stayed: the goto to a part written there and
        //again in the loop put the label in the commands thrown away. The control flow of a
        //function compiled by FlasCC, with each block replaced by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "jump ofs1016",
                "ofs00fc:",
                "ofs00fd:",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifne ofs0faa",
                "pushbyte 3",
                "setlocal 3",
                "ofs0180:",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifne ofs0edf",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifne ofs01d8",
                "pushbyte 6",
                "setlocal 3",
                "jump ofs02d7",
                "ofs01d8:",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifeq ofs01ea",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifeq ofs0287",
                "ofs01ea:",
                "pushbyte 9",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifne ofs0251",
                "pushbyte 10",
                "setlocal 3",
                "jump ofs02bc",
                "ofs0251:",
                "pushbyte 11",
                "setlocal 3",
                "jump ofs02bc",
                "ofs0287:",
                "pushbyte 12",
                "setlocal 3",
                "ofs02bc:",
                "pushbyte 13",
                "setlocal 3",
                "ofs02d7:",
                "pushbyte 14",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifeq ofs030d",
                "pushbyte 15",
                "setlocal 3",
                "ofs030d:",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifeq ofs0368",
                "pushbyte 17",
                "setlocal 3",
                "jump ofs04e4",
                "ofs0368:",
                "pushbyte 18",
                "setlocal 3",
                "ofs03e6:",
                "pushbyte 19",
                "setlocal 3",
                "ofs0419:",
                "pushbyte 20",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "iflt ofs03e6",
                "pushbyte 21",
                "setlocal 3",
                "ofs0465:",
                "pushbyte 22",
                "setlocal 3",
                "ofs0498:",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "iflt ofs0465",
                "pushbyte 24",
                "setlocal 3",
                "ofs04e4:",
                "pushbyte 25",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "iflt ofs075b",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifeq ofs0553",
                "pushbyte 27",
                "setlocal 3",
                "ofs0553:",
                "pushbyte 28",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifeq ofs056c",
                "pushbyte 29",
                "setlocal 3",
                "ofs056c:",
                "pushbyte 30",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifne ofs0722",
                "pushbyte 31",
                "setlocal 3",
                "ofs061c:",
                "pushbyte 32",
                "setlocal 3",
                "ofs064f:",
                "pushbyte 33",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "iflt ofs061c",
                "pushbyte 34",
                "setlocal 3",
                "ofs069b:",
                "pushbyte 35",
                "setlocal 3",
                "ofs06d2:",
                "pushbyte 36",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "iflt ofs069b",
                "pushbyte 37",
                "setlocal 3",
                "ofs0722:",
                "pushbyte 38",
                "setlocal 3",
                "ofs075b:",
                "pushbyte 39",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "ifeq ofs080e",
                "pushbyte 40",
                "setlocal 3",
                "jump ofs07fe",
                "ofs07eb:",
                "pushbyte 41",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifeq ofs08d3",
                "pushbyte 42",
                "setlocal 3",
                "ofs07fe:",
                "pushbyte 43",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "iflt ofs07eb",
                "ofs080e:",
                "pushbyte 44",
                "setlocal 3",
                "ofs0817:",
                "pushbyte 45",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "ifeq ofs08c6",
                "pushbyte 46",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "ifne ofs085d",
                "pushbyte 47",
                "setlocal 3",
                "jump ofs08d3",
                "ofs085d:",
                "pushbyte 48",
                "setlocal 3",
                "getlocal1",
                "pushbyte 22",
                "ifne ofs0880",
                "pushbyte 49",
                "setlocal 3",
                "jump ofs08d3",
                "ofs0880:",
                "pushbyte 50",
                "setlocal 3",
                "getlocal1",
                "pushbyte 23",
                "ifne ofs08a3",
                "pushbyte 51",
                "setlocal 3",
                "jump ofs08d3",
                "ofs08a3:",
                "pushbyte 52",
                "setlocal 3",
                "getlocal1",
                "pushbyte 24",
                "ifne ofs08c6",
                "pushbyte 53",
                "setlocal 3",
                "jump ofs08d3",
                "ofs08c6:",
                "pushbyte 54",
                "setlocal 3",
                "jump ofs0817",
                "ofs08d3:",
                "pushbyte 55",
                "setlocal 3",
                "getlocal1",
                "pushbyte 25",
                "ifeq ofs09f1",
                "pushbyte 56",
                "setlocal 3",
                "getlocal1",
                "pushbyte 26",
                "iflt ofs092d",
                "pushbyte 57",
                "setlocal 3",
                "getlocal1",
                "pushbyte 27",
                "iflt ofs0973",
                "ofs092d:",
                "pushbyte 58",
                "setlocal 3",
                "ofs0973:",
                "pushbyte 59",
                "setlocal 3",
                "jump ofs04e4",
                "ofs09aa:",
                "pushbyte 60",
                "setlocal 3",
                "ofs09ce:",
                "pushbyte 61",
                "setlocal 3",
                "ofs09f1:",
                "pushbyte 62",
                "setlocal 3",
                "getlocal1",
                "pushbyte 28",
                "ifeq ofs0a2d",
                "pushbyte 63",
                "setlocal 3",
                "ofs0a2d:",
                "pushbyte 64",
                "setlocal 3",
                "getlocal1",
                "pushbyte 29",
                "ifeq ofs0ade",
                "pushbyte 65",
                "setlocal 3",
                "getlocal1",
                "pushbyte 30",
                "ifgt ofs0ade",
                "pushbyte 66",
                "setlocal 3",
                "getlocal1",
                "pushbyte 31",
                "ifeq ofs0ade",
                "pushbyte 67",
                "setlocal 3",
                "jump ofs0ade",
                "ofs0aae:",
                "pushbyte 68",
                "setlocal 3",
                "ofs0ade:",
                "pushbyte 69",
                "setlocal 3",
                "returnvoid",
                "ofs0b10:",
                "pushbyte 70",
                "setlocal 3",
                "jump ofs0de9",
                "ofs0b26:",
                "pushbyte 71",
                "setlocal 3",
                "jump ofs0e5e",
                "ofs0b3c:",
                "pushbyte 72",
                "setlocal 3",
                "ofs0b46:",
                "pushbyte 73",
                "setlocal 3",
                "getlocal1",
                "pushbyte 32",
                "ifeq ofs0c83",
                "pushbyte 74",
                "setlocal 3",
                "getlocal1",
                "pushbyte 33",
                "iflt ofs0baf",
                "pushbyte 75",
                "setlocal 3",
                "getlocal1",
                "pushbyte 34",
                "ifle ofs0baf",
                "pushbyte 76",
                "setlocal 3",
                "getlocal1",
                "pushbyte 35",
                "ifne ofs0c83",
                "ofs0baf:",
                "pushbyte 77",
                "setlocal 3",
                "getlocal1",
                "pushbyte 36",
                "ifeq ofs0c2b",
                "pushbyte 78",
                "setlocal 3",
                "getlocal1",
                "pushbyte 37",
                "iflt ofs0c2b",
                "pushbyte 79",
                "setlocal 3",
                "getlocal1",
                "pushbyte 38",
                "ifle ofs0c2b",
                "pushbyte 80",
                "setlocal 3",
                "jump ofs0c83",
                "ofs0c2b:",
                "pushbyte 81",
                "setlocal 3",
                "ofs0c4e:",
                "pushbyte 82",
                "setlocal 3",
                "ofs0c83:",
                "pushbyte 83",
                "setlocal 3",
                "getlocal1",
                "pushbyte 39",
                "ifeq ofs0cf0",
                "pushbyte 84",
                "setlocal 3",
                "getlocal1",
                "pushbyte 40",
                "ifeq ofs0ce6",
                "pushbyte 85",
                "setlocal 3",
                "ofs0ce6:",
                "pushbyte 86",
                "setlocal 3",
                "jump ofs0d80",
                "ofs0cf0:",
                "pushbyte 87",
                "setlocal 3",
                "getlocal1",
                "pushbyte 41",
                "ifeq ofs0d80",
                "pushbyte 88",
                "setlocal 3",
                "jump ofs0d80",
                "ofs0d4b:",
                "pushbyte 89",
                "setlocal 3",
                "ofs0d80:",
                "pushbyte 90",
                "setlocal 3",
                "getlocal1",
                "pushbyte 42",
                "ifeq ofs09ce",
                "jump ofs09aa",
                "ofs0d8d:",
                "pushbyte 91",
                "setlocal 3",
                "jump ofs0e5e",
                "ofs0dc4:",
                "pushbyte 92",
                "setlocal 3",
                "ofs0de9:",
                "pushbyte 93",
                "setlocal 3",
                "getlocal1",
                "pushbyte 43",
                "ifeq ofs0aae",
                "pushbyte 94",
                "setlocal 3",
                "getlocal1",
                "pushbyte 44",
                "ifgt ofs0aae",
                "pushbyte 95",
                "setlocal 3",
                "getlocal1",
                "pushbyte 45",
                "ifeq ofs0aae",
                "pushbyte 96",
                "setlocal 3",
                "jump ofs0aae",
                "ofs0e5e:",
                "pushbyte 97",
                "setlocal 3",
                "getlocal1",
                "pushbyte 46",
                "ifeq ofs0de9",
                "pushbyte 98",
                "setlocal 3",
                "getlocal1",
                "pushbyte 47",
                "ifgt ofs0de9",
                "pushbyte 99",
                "setlocal 3",
                "getlocal1",
                "pushbyte 48",
                "ifeq ofs0de9",
                "pushbyte 0",
                "setlocal 3",
                "jump ofs0de9",
                "ofs0edf:",
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 49",
                "ifgt ofs0f52",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 50",
                "ifgt ofs0f23",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 51",
                "iflt ofs0b10",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 52",
                "ifeq ofs0419",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 53",
                "iflt ofs0498",
                "jump ofs0fa5",
                "ofs0f23:",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 54",
                "iflt ofs0b26",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 55",
                "ifeq ofs064f",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 56",
                "iflt ofs06d2",
                "jump ofs0fa5",
                "ofs0f52:",
                "pushbyte 9",
                "setlocal 3",
                "getlocal1",
                "pushbyte 57",
                "ifgt ofs0f8a",
                "pushbyte 10",
                "setlocal 3",
                "getlocal1",
                "pushbyte 58",
                "iflt ofs0b46",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 59",
                "iflt ofs0b3c",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 60",
                "ifeq ofs0d8d",
                "jump ofs0fa5",
                "ofs0f8a:",
                "pushbyte 13",
                "setlocal 3",
                "getlocal1",
                "pushbyte 61",
                "ifeq ofs0c4e",
                "pushbyte 14",
                "setlocal 3",
                "getlocal1",
                "pushbyte 62",
                "ifeq ofs0d4b",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 63",
                "ifeq ofs0dc4",
                "ofs0fa5:",
                "jump ofs0fa5",
                "ofs0faa:",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "lookupswitch ofs0fb9, [ofs0fbd]",
                "ofs0fb9:",
                "jump ofs0fc1",
                "ofs0fbd:",
                "jump ofs0180",
                "ofs0fc1:",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "throw",
                "ofs0fc7:",
                "ofs0fc8:",
                "pushbyte 18",
                "setlocal 3",
                "getlocal1",
                "pushbyte 64",
                "ifnge ofs0fe8",
                "pushbyte 19",
                "setlocal 3",
                "getlocal1",
                "throw",
                "ofs0fe8:",
                "pushbyte 20",
                "setlocal 3",
                "ofs1016:",
                "jump ofs00fc"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertJumpsHaveTargets(res);
    }

    @Test
    public void testWhileTrueContinuationWithBreakOfTheWhile() throws Exception {
        //while (true) { if (c) {...} rest; continue outer; } became while (c) {...} rest;, with
        //the rest moved out of the while, but there it had a break of the while: the break had
        //no loop. The control flow of a function compiled by FlasCC, with each block replaced
        //by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifne ofs0069",
                "pushbyte 2",
                "setlocal 3",
                "ofs0069:",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifne ofs0083",
                "pushbyte 4",
                "setlocal 3",
                "ofs0083:",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifne ofs0aab",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifne ofs0343",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifne ofs00d7",
                "pushbyte 8",
                "setlocal 3",
                "jump ofs0aab",
                "ofs00d7:",
                "pushbyte 9",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifeq ofs0332",
                "pushbyte 10",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifeq ofs0315",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifeq ofs0315",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifne ofs0127",
                "pushbyte 13",
                "setlocal 3",
                "ofs0127:",
                "pushbyte 14",
                "setlocal 3",
                "jump ofs02f3",
                "ofs013b:",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifeq ofs01a5",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifne ofs019d",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifne ofs0300",
                "ofs019d:",
                "pushbyte 18",
                "setlocal 3",
                "ofs01a5:",
                "pushbyte 19",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "iflt ofs02c6",
                "pushbyte 20",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifne ofs02b3",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "ifgt ofs022e",
                "pushbyte 22",
                "setlocal 3",
                "ofs022e:",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "ifgt ofs0252",
                "pushbyte 24",
                "setlocal 3",
                "ofs0252:",
                "pushbyte 25",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "ifeq ofs025f",
                "pushbyte 26",
                "setlocal 3",
                "ofs025f:",
                "pushbyte 27",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifne ofs02b3",
                "pushbyte 28",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "iflt ofs028c",
                "pushbyte 29",
                "setlocal 3",
                "ofs028c:",
                "pushbyte 30",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "iflt ofs029b",
                "pushbyte 31",
                "setlocal 3",
                "ofs029b:",
                "pushbyte 32",
                "setlocal 3",
                "jump ofs02d9",
                "ofs02b3:",
                "pushbyte 33",
                "setlocal 3",
                "jump ofs02d9",
                "ofs02c6:",
                "pushbyte 34",
                "setlocal 3",
                "jump ofs0300",
                "ofs02d9:",
                "pushbyte 35",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "iflt ofs0300",
                "pushbyte 36",
                "setlocal 3",
                "ofs02f3:",
                "pushbyte 37",
                "setlocal 3",
                "getlocal1",
                "pushbyte 22",
                "ifgt ofs013b",
                "jump ofs0315",
                "ofs0300:",
                "pushbyte 38",
                "setlocal 3",
                "jump ofs0aab",
                "ofs0315:",
                "pushbyte 39",
                "setlocal 3",
                "ofs0332:",
                "pushbyte 40",
                "setlocal 3",
                "jump ofs03ae",
                "ofs0343:",
                "pushbyte 41",
                "setlocal 3",
                "getlocal1",
                "pushbyte 23",
                "ifeq ofs03ae",
                "pushbyte 42",
                "setlocal 3",
                "getlocal1",
                "pushbyte 24",
                "ifeq ofs0383",
                "pushbyte 43",
                "setlocal 3",
                "ofs0383:",
                "pushbyte 44",
                "setlocal 3",
                "getlocal1",
                "pushbyte 25",
                "ifeq ofs03ae",
                "pushbyte 45",
                "setlocal 3",
                "jump ofs0aab",
                "ofs03ae:",
                "pushbyte 46",
                "setlocal 3",
                "getlocal1",
                "pushbyte 26",
                "ifne ofs03df",
                "pushbyte 47",
                "setlocal 3",
                "ofs03df:",
                "pushbyte 48",
                "setlocal 3",
                "getlocal1",
                "pushbyte 27",
                "ifeq ofs08df",
                "pushbyte 49",
                "setlocal 3",
                "ofs0406:",
                "pushbyte 50",
                "setlocal 3",
                "getlocal1",
                "pushbyte 28",
                "iflt ofs0691",
                "pushbyte 51",
                "setlocal 3",
                "ofs041f:",
                "pushbyte 52",
                "setlocal 3",
                "getlocal1",
                "pushbyte 29",
                "iflt ofs0678",
                "pushbyte 53",
                "setlocal 3",
                "getlocal1",
                "pushbyte 30",
                "ifne ofs0678",
                "pushbyte 54",
                "setlocal 3",
                "getlocal1",
                "pushbyte 31",
                "ifeq ofs0678",
                "pushbyte 55",
                "setlocal 3",
                "getlocal1",
                "pushbyte 32",
                "ifeq ofs0678",
                "pushbyte 56",
                "setlocal 3",
                "getlocal1",
                "pushbyte 33",
                "ifne ofs0485",
                "pushbyte 57",
                "setlocal 3",
                "ofs0485:",
                "pushbyte 58",
                "setlocal 3",
                "jump ofs066f",
                "ofs049a:",
                "pushbyte 59",
                "setlocal 3",
                "getlocal1",
                "pushbyte 34",
                "ifeq ofs0506",
                "pushbyte 60",
                "setlocal 3",
                "getlocal1",
                "pushbyte 35",
                "ifne ofs04fe",
                "pushbyte 61",
                "setlocal 3",
                "getlocal1",
                "pushbyte 36",
                "ifne ofs064c",
                "ofs04fe:",
                "pushbyte 62",
                "setlocal 3",
                "ofs0506:",
                "pushbyte 63",
                "setlocal 3",
                "getlocal1",
                "pushbyte 37",
                "iflt ofs062f",
                "pushbyte 64",
                "setlocal 3",
                "getlocal1",
                "pushbyte 38",
                "ifne ofs061b",
                "pushbyte 65",
                "setlocal 3",
                "getlocal1",
                "pushbyte 39",
                "ifgt ofs0593",
                "pushbyte 66",
                "setlocal 3",
                "ofs0593:",
                "pushbyte 67",
                "setlocal 3",
                "getlocal1",
                "pushbyte 40",
                "ifgt ofs05b8",
                "pushbyte 68",
                "setlocal 3",
                "ofs05b8:",
                "pushbyte 69",
                "setlocal 3",
                "getlocal1",
                "pushbyte 41",
                "ifeq ofs05c5",
                "pushbyte 70",
                "setlocal 3",
                "ofs05c5:",
                "pushbyte 71",
                "setlocal 3",
                "getlocal1",
                "pushbyte 42",
                "ifne ofs061b",
                "pushbyte 72",
                "setlocal 3",
                "getlocal1",
                "pushbyte 43",
                "iflt ofs05f3",
                "pushbyte 73",
                "setlocal 3",
                "ofs05f3:",
                "pushbyte 74",
                "setlocal 3",
                "getlocal1",
                "pushbyte 44",
                "iflt ofs0602",
                "pushbyte 75",
                "setlocal 3",
                "ofs0602:",
                "pushbyte 76",
                "setlocal 3",
                "jump ofs0643",
                "ofs061b:",
                "pushbyte 77",
                "setlocal 3",
                "jump ofs0643",
                "ofs062f:",
                "pushbyte 78",
                "setlocal 3",
                "jump ofs064c",
                "ofs0643:",
                "pushbyte 79",
                "setlocal 3",
                "getlocal1",
                "pushbyte 45",
                "ifgt ofs065e",
                "ofs064c:",
                "pushbyte 80",
                "setlocal 3",
                "jump ofs0678",
                "ofs065e:",
                "pushbyte 81",
                "setlocal 3",
                "ofs066f:",
                "pushbyte 82",
                "setlocal 3",
                "getlocal1",
                "pushbyte 46",
                "ifgt ofs049a",
                "ofs0678:",
                "pushbyte 83",
                "setlocal 3",
                "getlocal1",
                "pushbyte 47",
                "ifne ofs041f",
                "ofs0691:",
                "pushbyte 84",
                "setlocal 3",
                "getlocal1",
                "pushbyte 48",
                "ifne ofs0406",
                "pushbyte 85",
                "setlocal 3",
                "getlocal1",
                "pushbyte 49",
                "ifne ofs08df",
                "pushbyte 86",
                "setlocal 3",
                "getlocal1",
                "pushbyte 50",
                "ifeq ofs08df",
                "pushbyte 87",
                "setlocal 3",
                "getlocal1",
                "pushbyte 51",
                "ifeq ofs08df",
                "pushbyte 88",
                "setlocal 3",
                "getlocal1",
                "pushbyte 52",
                "ifne ofs06f9",
                "pushbyte 89",
                "setlocal 3",
                "ofs06f9:",
                "pushbyte 90",
                "setlocal 3",
                "jump ofs08d6",
                "ofs070d:",
                "pushbyte 91",
                "setlocal 3",
                "getlocal1",
                "pushbyte 53",
                "ifeq ofs0777",
                "pushbyte 92",
                "setlocal 3",
                "getlocal1",
                "pushbyte 54",
                "ifne ofs076f",
                "pushbyte 93",
                "setlocal 3",
                "getlocal1",
                "pushbyte 55",
                "ifne ofs08b4",
                "ofs076f:",
                "pushbyte 94",
                "setlocal 3",
                "ofs0777:",
                "pushbyte 95",
                "setlocal 3",
                "getlocal1",
                "pushbyte 56",
                "iflt ofs0898",
                "pushbyte 96",
                "setlocal 3",
                "getlocal1",
                "pushbyte 57",
                "ifne ofs0885",
                "pushbyte 97",
                "setlocal 3",
                "getlocal1",
                "pushbyte 58",
                "ifgt ofs0800",
                "pushbyte 98",
                "setlocal 3",
                "ofs0800:",
                "pushbyte 99",
                "setlocal 3",
                "getlocal1",
                "pushbyte 59",
                "ifgt ofs0824",
                "pushbyte 0",
                "setlocal 3",
                "ofs0824:",
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 60",
                "ifeq ofs0831",
                "pushbyte 2",
                "setlocal 3",
                "ofs0831:",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 61",
                "ifne ofs0885",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 62",
                "iflt ofs085e",
                "pushbyte 5",
                "setlocal 3",
                "ofs085e:",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 63",
                "iflt ofs086d",
                "pushbyte 7",
                "setlocal 3",
                "ofs086d:",
                "pushbyte 8",
                "setlocal 3",
                "jump ofs08ab",
                "ofs0885:",
                "pushbyte 9",
                "setlocal 3",
                "jump ofs08ab",
                "ofs0898:",
                "pushbyte 10",
                "setlocal 3",
                "jump ofs08b4",
                "ofs08ab:",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 64",
                "ifgt ofs08c5",
                "ofs08b4:",
                "pushbyte 12",
                "setlocal 3",
                "jump ofs08df",
                "ofs08c5:",
                "pushbyte 13",
                "setlocal 3",
                "ofs08d6:",
                "pushbyte 14",
                "setlocal 3",
                "getlocal1",
                "pushbyte 65",
                "ifgt ofs070d",
                "ofs08df:",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 66",
                "iflt ofs0a0e",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 67",
                "ifeq ofs0a38",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 68",
                "ifgt ofs0976",
                "pushbyte 18",
                "setlocal 3",
                "ofs0976:",
                "pushbyte 19",
                "setlocal 3",
                "getlocal1",
                "pushbyte 69",
                "ifgt ofs099a",
                "pushbyte 20",
                "setlocal 3",
                "ofs099a:",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 70",
                "ifeq ofs09a7",
                "pushbyte 22",
                "setlocal 3",
                "ofs09a7:",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "pushbyte 71",
                "ifne ofs09fb",
                "pushbyte 24",
                "setlocal 3",
                "getlocal1",
                "pushbyte 72",
                "iflt ofs09d4",
                "pushbyte 25",
                "setlocal 3",
                "ofs09d4:",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 73",
                "iflt ofs09e3",
                "pushbyte 27",
                "setlocal 3",
                "ofs09e3:",
                "pushbyte 28",
                "setlocal 3",
                "jump ofs0a55",
                "ofs09fb:",
                "pushbyte 29",
                "setlocal 3",
                "jump ofs0a38",
                "ofs0a0e:",
                "pushbyte 30",
                "setlocal 3",
                "getlocal1",
                "pushbyte 74",
                "ifgt ofs0a55",
                "pushbyte 31",
                "setlocal 3",
                "jump ofs0a93",
                "ofs0a38:",
                "pushbyte 32",
                "setlocal 3",
                "jump ofs0aab",
                "ofs0a55:",
                "pushbyte 33",
                "setlocal 3",
                "getlocal1",
                "pushbyte 75",
                "ifgt ofs0aab",
                "pushbyte 34",
                "setlocal 3",
                "getlocal1",
                "pushbyte 76",
                "ifne ofs0a93",
                "pushbyte 35",
                "setlocal 3",
                "jump ofs0aab",
                "ofs0a93:",
                "pushbyte 36",
                "setlocal 3",
                "ofs0aab:",
                "pushbyte 37",
                "setlocal 3",
                "returnvoid"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertJumpsHaveTargets(res);
    }

    @Test
    public void testBreakToLoopNotInResultAfterProcessIfsMovedTarget() throws Exception {
        //a break to a loop that is not in the result becomes a goto to the code it jumps to,
        //but processIfs had moved that code out of the list where it was written (the commands
        //of a branch go to the list of the if): the break stayed, to a loop that is not there.
        //The control flow of a function compiled by FlasCC, with each block replaced by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifeq ofs00cc",
                "pushbyte 2",
                "setlocal 3",
                "ofs00cc:",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifne ofs010b",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifeq ofs0130",
                "ofs010b:",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifeq ofs0141",
                "jump ofs019b",
                "ofs0130:",
                "pushbyte 6",
                "setlocal 3",
                "jump ofs07b1",
                "ofs0141:",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "iflt ofs017d",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifeq ofs016c",
                "pushbyte 9",
                "setlocal 3",
                "ofs016c:",
                "pushbyte 10",
                "setlocal 3",
                "jump ofs07b1",
                "ofs017d:",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifne ofs07b1",
                "pushbyte 12",
                "setlocal 3",
                "jump ofs07b1",
                "ofs019b:",
                "pushbyte 13",
                "setlocal 3",
                "jump ofs06db",
                "ofs01ac:",
                "pushbyte 14",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifeq ofs021d",
                "pushbyte 15",
                "setlocal 3",
                "ofs021d:",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifne ofs0258",
                "ofs0243:",
                "pushbyte 17",
                "setlocal 3",
                "jump ofs0258",
                "ofs024f:",
                "pushbyte 18",
                "setlocal 3",
                "ofs0258:",
                "pushbyte 19",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifne ofs0343",
                "pushbyte 20",
                "setlocal 3",
                "jump ofs07b1",
                "ofs0343:",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifne ofs03ac",
                "pushbyte 22",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifne ofs03da",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifeq ofs06c6",
                "pushbyte 24",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifeq ofs06db",
                "pushbyte 25",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "ifeq ofs07b1",
                "jump ofs06c6",
                "ofs03ac:",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "ifne ofs03da",
                "pushbyte 27",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "ifne ofs03d1",
                "pushbyte 28",
                "setlocal 3",
                "ofs03d1:",
                "pushbyte 29",
                "setlocal 3",
                "jump ofs0422",
                "ofs03da:",
                "pushbyte 30",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifne ofs0411",
                "pushbyte 31",
                "setlocal 3",
                "jump ofs06c6",
                "ofs0411:",
                "pushbyte 32",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "ifne ofs0447",
                "ofs0422:",
                "pushbyte 33",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "ifne ofs0447",
                "pushbyte 34",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "ifeq ofs06c6",
                "ofs0447:",
                "pushbyte 35",
                "setlocal 3",
                "getlocal1",
                "pushbyte 22",
                "iflt ofs047d",
                "pushbyte 36",
                "setlocal 3",
                "getlocal1",
                "pushbyte 23",
                "ifeq ofs048a",
                "pushbyte 37",
                "setlocal 3",
                "getlocal1",
                "pushbyte 24",
                "ifeq ofs048a",
                "ofs047d:",
                "pushbyte 38",
                "setlocal 3",
                "getlocal1",
                "pushbyte 25",
                "iflt ofs04b1",
                "jump ofs0568",
                "ofs048a:",
                "pushbyte 39",
                "setlocal 3",
                "getlocal1",
                "pushbyte 26",
                "ifeq ofs0499",
                "pushbyte 40",
                "setlocal 3",
                "ofs0499:",
                "pushbyte 41",
                "setlocal 3",
                "getlocal1",
                "pushbyte 27",
                "ifne ofs0632",
                "pushbyte 42",
                "setlocal 3",
                "jump ofs0632",
                "ofs04b1:",
                "pushbyte 43",
                "setlocal 3",
                "getlocal1",
                "pushbyte 28",
                "iflt ofs04e1",
                "pushbyte 44",
                "setlocal 3",
                "getlocal1",
                "pushbyte 29",
                "ifeq ofs0632",
                "pushbyte 45",
                "setlocal 3",
                "getlocal1",
                "pushbyte 30",
                "ifeq ofs0568",
                "ofs04e1:",
                "pushbyte 46",
                "setlocal 3",
                "getlocal1",
                "pushbyte 31",
                "iflt ofs0504",
                "pushbyte 47",
                "setlocal 3",
                "getlocal1",
                "pushbyte 32",
                "ifeq ofs04ff",
                "pushbyte 48",
                "setlocal 3",
                "ofs04ff:",
                "jump ofs0568",
                "ofs0504:",
                "pushbyte 49",
                "setlocal 3",
                "getlocal1",
                "pushbyte 33",
                "ifeq ofs0568",
                "pushbyte 50",
                "setlocal 3",
                "ofs0568:",
                "pushbyte 51",
                "setlocal 3",
                "getlocal1",
                "pushbyte 34",
                "ifgt ofs0632",
                "pushbyte 52",
                "setlocal 3",
                "getlocal1",
                "pushbyte 35",
                "iflt ofs05a1",
                "pushbyte 53",
                "setlocal 3",
                "getlocal1",
                "pushbyte 36",
                "ifeq ofs0632",
                "pushbyte 54",
                "setlocal 3",
                "getlocal1",
                "pushbyte 37",
                "ifeq ofs0632",
                "ofs05a1:",
                "pushbyte 55",
                "setlocal 3",
                "getlocal1",
                "pushbyte 38",
                "iflt ofs05cc",
                "pushbyte 56",
                "setlocal 3",
                "getlocal1",
                "pushbyte 39",
                "ifeq ofs05c7",
                "pushbyte 57",
                "setlocal 3",
                "ofs05c7:",
                "jump ofs0632",
                "ofs05cc:",
                "pushbyte 58",
                "setlocal 3",
                "getlocal1",
                "pushbyte 40",
                "ifeq ofs0632",
                "pushbyte 59",
                "setlocal 3",
                "ofs0632:",
                "pushbyte 60",
                "setlocal 3",
                "getlocal1",
                "pushbyte 41",
                "iflt ofs0695",
                "pushbyte 61",
                "setlocal 3",
                "getlocal1",
                "pushbyte 42",
                "iflt ofs0675",
                "pushbyte 62",
                "setlocal 3",
                "ofs0675:",
                "pushbyte 63",
                "setlocal 3",
                "getlocal1",
                "pushbyte 43",
                "ifeq ofs06c6",
                "jump ofs07b1",
                "ofs0695:",
                "pushbyte 64",
                "setlocal 3",
                "getlocal1",
                "pushbyte 44",
                "iflt ofs06ba",
                "pushbyte 65",
                "setlocal 3",
                "jump ofs07b1",
                "ofs06ba:",
                "pushbyte 66",
                "setlocal 3",
                "ofs06c6:",
                "pushbyte 67",
                "setlocal 3",
                "getlocal1",
                "pushbyte 45",
                "ifeq ofs07b1",
                "ofs06db:",
                "pushbyte 68",
                "setlocal 3",
                "getlocal1",
                "pushbyte 46",
                "ifne ofs07a8",
                "ofs06e8:",
                "pushbyte 69",
                "setlocal 3",
                "getlocal1",
                "pushbyte 47",
                "ifeq ofs07b1",
                "jump ofs0732",
                "ofs06f5:",
                "pushbyte 70",
                "setlocal 3",
                "getlocal1",
                "pushbyte 48",
                "ifeq ofs06e8",
                "jump ofs0243",
                "ofs0710:",
                "pushbyte 71",
                "setlocal 3",
                "getlocal1",
                "pushbyte 49",
                "ifeq ofs06f5",
                "jump ofs024f",
                "ofs0732:",
                "pushbyte 72",
                "setlocal 3",
                "getlocal1",
                "pushbyte 50",
                "ifeq ofs0710",
                "pushbyte 73",
                "setlocal 3",
                "jump ofs0710",
                "ofs07a8:",
                "pushbyte 74",
                "setlocal 3",
                "getlocal1",
                "pushbyte 51",
                "ifne ofs01ac",
                "ofs07b1:",
                "pushbyte 75",
                "setlocal 3",
                "returnvoid"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertJumpsHaveTargets(res);
    }

    @Test
    public void testCatchInsideLoopKeepsItsCode() throws Exception {
        //a function compiled by FlasCC that calls setjmp is a loop around a try, and the
        //catch of the longjmp throws it on or sets the registers from it and goes round the
        //loop again: the throw became the break of the loop and the rest of the catch its
        //precontinue, both written out of the catch, where the catch variable is not known.
        //The control flow of the try is reduced to marks
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "jump ofs03a4",
                "ofs004c:",
                "ofs004d:",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifne ofs0338",
                "pushbyte 3",
                "setlocal 3",
                "ofs00c8:",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifne ofs0322",
                "pushbyte 5",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifeq ofs02f0",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifeq ofs0228",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "iflt ofs015d",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifle ofs015d",
                "pushbyte 9",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifne ofs0228",
                "ofs015d:",
                "pushbyte 10",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifeq ofs01d4",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "iflt ofs01d4",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifle ofs01d4",
                "pushbyte 13",
                "setlocal 3",
                "jump ofs0228",
                "ofs01d4:",
                "pushbyte 14",
                "setlocal 3",
                "ofs01f5:",
                "pushbyte 15",
                "setlocal 3",
                "ofs0228:",
                "pushbyte 16",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifne ofs02f0",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifeq ofs02f0",
                "pushbyte 18",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifne ofs02f0",
                "pushbyte 19",
                "setlocal 3",
                "ofs02f0:",
                "pushbyte 20",
                "setlocal 3",
                "returnvoid",
                "ofs0322:",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifeq ofs01f5",
                "ofs0333:",
                "jump ofs0333",
                "ofs0338:",
                "pushbyte 22",
                "setlocal 3",
                "getlocal1",
                "lookupswitch ofs0347, [ofs034b]",
                "ofs0347:",
                "jump ofs034f",
                "ofs034b:",
                "jump ofs00c8",
                "ofs034f:",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "throw",
                "ofs0355:",
                "ofs0356:",
                "getlocal0",
                "dup",
                "pushscope",
                "pushscope",
                "newcatch 0",
                "dup",
                "setlocal 19",
                "dup",
                "pushscope",
                "swap",
                "setslot 1",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"esp\")",
                "getlocal 16",
                "ifnge ofs0376",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "throw",
                "ofs0376:",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"esp\")",
                "dup",
                "convert_i",
                "setlocal1",
                "findproperty QName(PackageNamespace(\"flascc\"),\"ESP\")",
                "swap",
                "setproperty QName(PackageNamespace(\"flascc\"),\"ESP\")",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"retval\")",
                "findproperty QName(PackageNamespace(\"flascc\"),\"eax\")",
                "swap",
                "setproperty QName(PackageNamespace(\"flascc\"),\"eax\")",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"sjid\")",
                "convert_i",
                "setlocal 17",
                "popscope",
                "ofs03a4:",
                "jump ofs004c",
                "try from ofs004d to ofs0355 target ofs0356 type QName(PackageNamespace(\"flascc\"),\"LongJmp\") name QName(PackageInternalNs(\"module\"),\"l\") end"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        int start = res.indexOf("catch(l:LongJmp)");
        assertTrue(start >= 0, res);
        int open = res.indexOf("{", start);
        int depth = 0;
        int end = open;
        for (; end < res.length(); end++) {
            if (res.charAt(end) == '{') {
                depth++;
            } else if (res.charAt(end) == '}') {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
        }
        String catchBody = res.substring(open, end);
        assertTrue(catchBody.contains("throw l;"), res);
        assertTrue(catchBody.contains("l.sjid"), res);
        assertTrue(!res.substring(end).contains("l."), res);
        assertTrue(!res.substring(end).contains("throw l"), res);
    }

    @Test
    public void testEmptySwitchCaseBeforeDefaultHasBreak() throws Exception {
        //a case that goes straight to the end of the switch had no commands and no break,
        //and fell through to the default (FlasCC writes the return of a longjmp as
        //switch (id - 1) { case 0: goto back; default: throw "bad longjmp"; })
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "iftrue sw",
                "pushbyte 3",
                "setlocal 3",
                "jump after",
                "sw:",
                "getlocal1",
                "lookupswitch dflt, [after]",
                "dflt:",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "throw",
                "after:",
                "pushbyte 4",
                "setlocal 3"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertTrue(java.util.regex.Pattern.compile("case 0:\\s*break;\\s*default:").matcher(res).find(), res);
    }

    @Test
    public void testGotoToLoopHeadLabelBeforeLoop() throws Exception {
        //a goto to the first part of a loop already written put the label after the loop, where
        //the list marks the part, at the end of the list, and the label was lost. A function compiled
        //by FlasCC that calls setjmp, with the control flow of the try reduced to marks
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "jump ofs06dd",
                "ofs0080:",
                "ofs0081:",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifne ofs0671",
                "pushbyte 3",
                "setlocal 3",
                "ofs0112:",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifne ofs0648",
                "pushbyte 5",
                "setlocal 3",
                "jump ofs016b",
                "ofs0156:",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifeq ofs04eb",
                "ofs016b:",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifeq ofs0156",
                "pushbyte 8",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifeq ofs01f1",
                "pushbyte 9",
                "setlocal 3",
                "jump ofs01e0",
                "ofs01ca:",
                "pushbyte 10",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifeq ofs02b8",
                "pushbyte 11",
                "setlocal 3",
                "ofs01e0:",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "iflt ofs01ca",
                "ofs01f1:",
                "pushbyte 13",
                "setlocal 3",
                "ofs01fa:",
                "pushbyte 14",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifeq ofs02ab",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifne ofs023f",
                "pushbyte 16",
                "setlocal 3",
                "jump ofs02b8",
                "ofs023f:",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifne ofs0263",
                "pushbyte 18",
                "setlocal 3",
                "jump ofs02b8",
                "ofs0263:",
                "pushbyte 19",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifne ofs0287",
                "pushbyte 20",
                "setlocal 3",
                "jump ofs02b8",
                "ofs0287:",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifne ofs02ab",
                "pushbyte 22",
                "setlocal 3",
                "jump ofs02b8",
                "ofs02ab:",
                "pushbyte 23",
                "setlocal 3",
                "jump ofs01fa",
                "ofs02b8:",
                "pushbyte 24",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifeq ofs02d1",
                "pushbyte 25",
                "setlocal 3",
                "ofs02d1:",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifne ofs0479",
                "pushbyte 27",
                "setlocal 3",
                "ofs0381:",
                "pushbyte 28",
                "setlocal 3",
                "ofs03b4:",
                "pushbyte 29",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "iflt ofs0381",
                "pushbyte 30",
                "setlocal 3",
                "ofs0400:",
                "pushbyte 31",
                "setlocal 3",
                "ofs0430:",
                "pushbyte 32",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "iflt ofs0400",
                "pushbyte 33",
                "setlocal 3",
                "ofs0479:",
                "pushbyte 34",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "ifeq ofs04be",
                "pushbyte 35",
                "setlocal 3",
                "ofs049c:",
                "pushbyte 36",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifne ofs049c",
                "ofs04be:",
                "pushbyte 37",
                "setlocal 3",
                "ofs04eb:",
                "pushbyte 38",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "ifeq ofs060b",
                "pushbyte 39",
                "setlocal 3",
                "ofs060b:",
                "pushbyte 40",
                "setlocal 3",
                "returnvoid",
                "ofs0648:",
                "pushbyte 41",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "ifeq ofs03b4",
                "pushbyte 42",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "iflt ofs0430",
                "ofs066c:",
                "jump ofs066c",
                "ofs0671:",
                "pushbyte 43",
                "setlocal 3",
                "getlocal1",
                "lookupswitch ofs0680, [ofs0684]",
                "ofs0680:",
                "jump ofs0688",
                "ofs0684:",
                "jump ofs0112",
                "ofs0688:",
                "pushbyte 44",
                "setlocal 3",
                "getlocal1",
                "throw",
                "ofs068e:",
                "ofs068f:",
                "getlocal0",
                "dup",
                "pushscope",
                "pushscope",
                "newcatch 0",
                "dup",
                "setlocal 32",
                "dup",
                "pushscope",
                "swap",
                "setslot 1",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"esp\")",
                "getlocal 29",
                "ifnge ofs06af",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "throw",
                "ofs06af:",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"esp\")",
                "dup",
                "convert_i",
                "setlocal1",
                "findproperty QName(PackageNamespace(\"flascc\"),\"ESP\")",
                "swap",
                "setproperty QName(PackageNamespace(\"flascc\"),\"ESP\")",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"retval\")",
                "findproperty QName(PackageNamespace(\"flascc\"),\"eax\")",
                "swap",
                "setproperty QName(PackageNamespace(\"flascc\"),\"eax\")",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"sjid\")",
                "convert_i",
                "setlocal 30",
                "popscope",
                "ofs06dd:",
                "jump ofs0080",
                "try from ofs0081 to ofs068e target ofs068f type QName(PackageNamespace(\"flascc\"),\"LongJmp\") name QName(PackageInternalNs(\"module\"),\"l\") end"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertJumpsHaveTargets(res);
    }

    @Test
    public void testContinueRunsPrecontinueWrittenAtEndOfLoop() throws Exception {
        //a loop whose type is not found gets its precontinue (here the decrement, the mark
        //_loc3_ = 8) at the end of the body, and each continue skipped it: an endless loop.
        //The control flow of a function compiled by FlasCC, with each block replaced by a mark
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifne ofs00c2",
                "pushbyte 2",
                "setlocal 3",
                "jump ofs03b8",
                "ofs00c2:",
                "pushbyte 3",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifeq ofs00e1",
                "pushbyte 4",
                "setlocal 3",
                "ofs00e1:",
                "pushbyte 5",
                "setlocal 3",
                "jump ofs0392",
                "ofs00fc:",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifne ofs0163",
                "pushbyte 7",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifne ofs0163",
                "ofs0156:",
                "pushbyte 8",
                "setlocal 3",
                "jump ofs0392",
                "ofs0163:",
                "pushbyte 9",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifeq ofs01a1",
                "pushbyte 10",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifeq ofs0198",
                "pushbyte 11",
                "setlocal 3",
                "ofs0198:",
                "pushbyte 12",
                "setlocal 3",
                "ofs01a1:",
                "pushbyte 13",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "ifeq ofs0156",
                "pushbyte 14",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifne ofs0237",
                "pushbyte 15",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifeq ofs0237",
                "pushbyte 16",
                "setlocal 3",
                "ofs0237:",
                "pushbyte 17",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "iflt ofs0257",
                "pushbyte 18",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifne ofs0257",
                "pushbyte 19",
                "setlocal 3",
                "ofs0257:",
                "pushbyte 20",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifne ofs02f4",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "iflt ofs03b8",
                "pushbyte 22",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifeq ofs02c9",
                "pushbyte 23",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "ifeq ofs03b8",
                "jump ofs0156",
                "ofs02c9:",
                "pushbyte 24",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "ifeq ofs03b8",
                "pushbyte 25",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "ifeq ofs03b8",
                "jump ofs0156",
                "ofs02f4:",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifeq ofs031d",
                "pushbyte 27",
                "setlocal 3",
                "jump ofs03b8",
                "ofs031d:",
                "pushbyte 28",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "ifeq ofs0340",
                "pushbyte 29",
                "setlocal 3",
                "jump ofs0156",
                "ofs0340:",
                "pushbyte 30",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "ifeq ofs0367",
                "pushbyte 31",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "ifeq ofs0367",
                "pushbyte 32",
                "setlocal 3",
                "getlocal1",
                "pushbyte 22",
                "ifeq ofs0378",
                "ofs0367:",
                "pushbyte 33",
                "setlocal 3",
                "jump ofs03b8",
                "ofs0378:",
                "pushbyte 34",
                "setlocal 3",
                "jump ofs0156",
                "ofs0392:",
                "pushbyte 35",
                "setlocal 3",
                "getlocal1",
                "pushbyte 23",
                "ifne ofs00fc",
                "pushbyte 36",
                "setlocal 3",
                "getlocal1",
                "pushbyte 24",
                "ifne ofs03b0",
                "pushbyte 37",
                "setlocal 3",
                "ofs03b0:",
                "pushbyte 38",
                "setlocal 3",
                "ofs03b8:",
                "pushbyte 39",
                "setlocal 3",
                "returnvoid"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^(.*)\\n\\s*continue;").matcher(res);
        int continues = 0;
        while (m.find()) {
            continues++;
            assertEquals(m.group(1).trim(), "_loc3_ = 8;", res);
        }
        assertTrue(continues > 0, res);
    }

    @Test
    public void testGotoFindsLabelMovedIntoBlock() throws Exception {
        //the label of a part went into an if with the code after it (processIfs moves the
        //commands of a branch), and the goto to the part wrote a second label with the same
        //name where the code had been, before other code. A function compiled by FlasCC that
        //calls setjmp, with the control flow of the try reduced to marks
        String pCode = String.join("\n", new String[]{
                "pushbyte 1",
                "setlocal 3",
                "jump ofs248d",
                "ofs0034:",
                "ofs0035:",
                "pushbyte 2",
                "setlocal 3",
                "getlocal1",
                "pushbyte 1",
                "ifne ofs2422",
                "pushbyte 3",
                "setlocal 3",
                "ofs00d9:",
                "pushbyte 4",
                "setlocal 3",
                "getlocal1",
                "pushbyte 2",
                "ifne ofs22d2",
                "pushbyte 5",
                "setlocal 3",
                "ofs010c:",
                "pushbyte 6",
                "setlocal 3",
                "getlocal1",
                "pushbyte 3",
                "ifle ofs0164",
                "pushbyte 7",
                "setlocal 3",
                "jump ofs010c",
                "ofs0164:",
                "pushbyte 8",
                "setlocal 3",
                "jump ofs02fe",
                "ofs02ac:",
                "pushbyte 9",
                "setlocal 3",
                "ofs02fe:",
                "pushbyte 10",
                "setlocal 3",
                "ofs03e9:",
                "pushbyte 11",
                "setlocal 3",
                "getlocal1",
                "pushbyte 4",
                "ifne ofs03e9",
                "ofs0424:",
                "pushbyte 12",
                "setlocal 3",
                "getlocal1",
                "pushbyte 5",
                "ifne ofs0424",
                "pushbyte 13",
                "setlocal 3",
                "jump ofs0604",
                "ofs05b2:",
                "pushbyte 14",
                "setlocal 3",
                "ofs0604:",
                "pushbyte 15",
                "setlocal 3",
                "jump ofs0f08",
                "ofs0eb6:",
                "pushbyte 16",
                "setlocal 3",
                "ofs0f08:",
                "pushbyte 17",
                "setlocal 3",
                "jump ofs1050",
                "ofs0fa1:",
                "pushbyte 18",
                "setlocal 3",
                "getlocal1",
                "pushbyte 6",
                "ifne ofs105a",
                "pushbyte 19",
                "setlocal 3",
                "ofs1050:",
                "pushbyte 20",
                "setlocal 3",
                "getlocal1",
                "pushbyte 7",
                "iflt ofs0fa1",
                "ofs105a:",
                "pushbyte 21",
                "setlocal 3",
                "getlocal1",
                "pushbyte 8",
                "ifeq ofs106c",
                "pushbyte 22",
                "setlocal 3",
                "ofs106c:",
                "pushbyte 23",
                "setlocal 3",
                "ofs1081:",
                "pushbyte 24",
                "setlocal 3",
                "getlocal1",
                "pushbyte 9",
                "ifne ofs10ea",
                "pushbyte 25",
                "setlocal 3",
                "jump ofs1081",
                "ofs10ea:",
                "pushbyte 26",
                "setlocal 3",
                "getlocal1",
                "pushbyte 10",
                "ifgt ofs1162",
                "pushbyte 27",
                "setlocal 3",
                "ofs1162:",
                "pushbyte 28",
                "setlocal 3",
                "getlocal1",
                "pushbyte 11",
                "ifeq ofs11e3",
                "jump ofs1081",
                "ofs1191:",
                "pushbyte 29",
                "setlocal 3",
                "ofs11e3:",
                "pushbyte 30",
                "setlocal 3",
                "jump ofs12ab",
                "ofs1259:",
                "pushbyte 31",
                "setlocal 3",
                "ofs12ab:",
                "pushbyte 32",
                "setlocal 3",
                "ofs1397:",
                "pushbyte 33",
                "setlocal 3",
                "getlocal1",
                "pushbyte 12",
                "ifne ofs1397",
                "ofs13d7:",
                "pushbyte 34",
                "setlocal 3",
                "getlocal1",
                "pushbyte 13",
                "ifne ofs13d7",
                "pushbyte 35",
                "setlocal 3",
                "jump ofs15bd",
                "ofs156b:",
                "pushbyte 36",
                "setlocal 3",
                "ofs15bd:",
                "pushbyte 37",
                "setlocal 3",
                "jump ofs1ec1",
                "ofs1e6f:",
                "pushbyte 38",
                "setlocal 3",
                "ofs1ec1:",
                "pushbyte 39",
                "setlocal 3",
                "getlocal1",
                "pushbyte 14",
                "ifne ofs1f45",
                "pushbyte 40",
                "setlocal 3",
                "ofs1f45:",
                "pushbyte 41",
                "setlocal 3",
                "getlocal1",
                "pushbyte 15",
                "ifne ofs1fa1",
                "pushbyte 42",
                "setlocal 3",
                "ofs1fa1:",
                "pushbyte 43",
                "setlocal 3",
                "getlocal1",
                "pushbyte 16",
                "ifne ofs1ffd",
                "pushbyte 44",
                "setlocal 3",
                "ofs1ffd:",
                "pushbyte 45",
                "setlocal 3",
                "getlocal1",
                "pushbyte 17",
                "ifne ofs2059",
                "pushbyte 46",
                "setlocal 3",
                "ofs2059:",
                "pushbyte 47",
                "setlocal 3",
                "getlocal1",
                "pushbyte 18",
                "ifne ofs20b5",
                "pushbyte 48",
                "setlocal 3",
                "ofs20b5:",
                "pushbyte 49",
                "setlocal 3",
                "getlocal1",
                "pushbyte 19",
                "ifne ofs2111",
                "pushbyte 50",
                "setlocal 3",
                "ofs2111:",
                "pushbyte 51",
                "setlocal 3",
                "getlocal1",
                "pushbyte 20",
                "ifne ofs216d",
                "pushbyte 52",
                "setlocal 3",
                "ofs216d:",
                "pushbyte 53",
                "setlocal 3",
                "getlocal1",
                "pushbyte 21",
                "ifne ofs21c9",
                "pushbyte 54",
                "setlocal 3",
                "ofs21c9:",
                "pushbyte 55",
                "setlocal 3",
                "returnvoid",
                "ofs2212:",
                "pushbyte 56",
                "setlocal 3",
                "jump ofs22ae",
                "ofs2226:",
                "pushbyte 57",
                "setlocal 3",
                "jump ofs22ae",
                "ofs223a:",
                "pushbyte 58",
                "setlocal 3",
                "jump ofs22ae",
                "ofs224e:",
                "pushbyte 59",
                "setlocal 3",
                "jump ofs22ae",
                "ofs2262:",
                "pushbyte 60",
                "setlocal 3",
                "jump ofs22ae",
                "ofs2276:",
                "pushbyte 61",
                "setlocal 3",
                "jump ofs22ae",
                "ofs228a:",
                "pushbyte 62",
                "setlocal 3",
                "jump ofs22ae",
                "ofs229e:",
                "pushbyte 63",
                "setlocal 3",
                "ofs22ae:",
                "pushbyte 64",
                "setlocal 3",
                "ofs22d2:",
                "pushbyte 65",
                "setlocal 3",
                "getlocal1",
                "pushbyte 22",
                "ifgt ofs2383",
                "pushbyte 66",
                "setlocal 3",
                "getlocal1",
                "pushbyte 23",
                "ifgt ofs2334",
                "pushbyte 67",
                "setlocal 3",
                "getlocal1",
                "pushbyte 24",
                "ifgt ofs230d",
                "pushbyte 68",
                "setlocal 3",
                "getlocal1",
                "pushbyte 25",
                "ifeq ofs2212",
                "pushbyte 69",
                "setlocal 3",
                "getlocal1",
                "pushbyte 26",
                "ifeq ofs02ac",
                "jump ofs241d",
                "ofs230d:",
                "pushbyte 70",
                "setlocal 3",
                "getlocal1",
                "pushbyte 27",
                "ifeq ofs2226",
                "pushbyte 71",
                "setlocal 3",
                "getlocal1",
                "pushbyte 28",
                "iflt ofs2212",
                "pushbyte 72",
                "setlocal 3",
                "getlocal1",
                "pushbyte 29",
                "ifeq ofs05b2",
                "jump ofs241d",
                "ofs2334:",
                "pushbyte 73",
                "setlocal 3",
                "getlocal1",
                "pushbyte 30",
                "ifgt ofs2364",
                "pushbyte 74",
                "setlocal 3",
                "getlocal1",
                "pushbyte 31",
                "ifeq ofs223a",
                "pushbyte 75",
                "setlocal 3",
                "getlocal1",
                "pushbyte 32",
                "iflt ofs2212",
                "pushbyte 76",
                "setlocal 3",
                "getlocal1",
                "pushbyte 33",
                "ifeq ofs0eb6",
                "jump ofs241d",
                "ofs2364:",
                "pushbyte 77",
                "setlocal 3",
                "getlocal1",
                "pushbyte 34",
                "ifeq ofs224e",
                "pushbyte 78",
                "setlocal 3",
                "getlocal1",
                "pushbyte 35",
                "ifeq ofs2212",
                "pushbyte 79",
                "setlocal 3",
                "getlocal1",
                "pushbyte 36",
                "ifeq ofs1191",
                "jump ofs241d",
                "ofs2383:",
                "pushbyte 80",
                "setlocal 3",
                "getlocal1",
                "pushbyte 37",
                "ifgt ofs23d2",
                "pushbyte 81",
                "setlocal 3",
                "getlocal1",
                "pushbyte 38",
                "ifgt ofs23ab",
                "pushbyte 82",
                "setlocal 3",
                "getlocal1",
                "pushbyte 39",
                "ifeq ofs2262",
                "pushbyte 83",
                "setlocal 3",
                "getlocal1",
                "pushbyte 40",
                "ifeq ofs2212",
                "jump ofs241d",
                "ofs23ab:",
                "pushbyte 84",
                "setlocal 3",
                "getlocal1",
                "pushbyte 41",
                "ifeq ofs1259",
                "pushbyte 85",
                "setlocal 3",
                "getlocal1",
                "pushbyte 42",
                "ifeq ofs2276",
                "pushbyte 86",
                "setlocal 3",
                "getlocal1",
                "pushbyte 43",
                "iflt ofs2212",
                "jump ofs241d",
                "ofs23d2:",
                "pushbyte 87",
                "setlocal 3",
                "getlocal1",
                "pushbyte 44",
                "ifgt ofs2402",
                "pushbyte 88",
                "setlocal 3",
                "getlocal1",
                "pushbyte 45",
                "ifeq ofs156b",
                "pushbyte 89",
                "setlocal 3",
                "getlocal1",
                "pushbyte 46",
                "ifeq ofs228a",
                "pushbyte 90",
                "setlocal 3",
                "getlocal1",
                "pushbyte 47",
                "iflt ofs2212",
                "jump ofs241d",
                "ofs2402:",
                "pushbyte 91",
                "setlocal 3",
                "getlocal1",
                "pushbyte 48",
                "ifeq ofs1e6f",
                "pushbyte 92",
                "setlocal 3",
                "getlocal1",
                "pushbyte 49",
                "ifeq ofs229e",
                "pushbyte 93",
                "setlocal 3",
                "getlocal1",
                "pushbyte 50",
                "ifeq ofs2212",
                "ofs241d:",
                "jump ofs241d",
                "ofs2422:",
                "pushbyte 94",
                "setlocal 3",
                "getlocal1",
                "lookupswitch ofs2431, [ofs2435]",
                "ofs2431:",
                "jump ofs2439",
                "ofs2435:",
                "jump ofs00d9",
                "ofs2439:",
                "pushbyte 95",
                "setlocal 3",
                "getlocal1",
                "throw",
                "ofs243f:",
                "ofs2440:",
                "getlocal0",
                "dup",
                "pushscope",
                "pushscope",
                "newcatch 0",
                "dup",
                "setlocal 13",
                "dup",
                "pushscope",
                "swap",
                "setslot 1",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"esp\")",
                "getlocal1",
                "ifnge ofs245f",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "throw",
                "ofs245f:",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"esp\")",
                "dup",
                "convert_i",
                "setlocal2",
                "findproperty QName(PackageNamespace(\"flascc\"),\"ESP\")",
                "swap",
                "setproperty QName(PackageNamespace(\"flascc\"),\"ESP\")",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"retval\")",
                "findproperty QName(PackageNamespace(\"flascc\"),\"eax\")",
                "swap",
                "setproperty QName(PackageNamespace(\"flascc\"),\"eax\")",
                "getlex QName(PackageInternalNs(\"module\"),\"l\")",
                "getproperty QName(PackageNamespace(\"\"),\"sjid\")",
                "convert_i",
                "setlocal 10",
                "popscope",
                "ofs248d:",
                "jump ofs0034",
                "try from ofs0035 to ofs243f target ofs2440 type QName(PackageNamespace(\"flascc\"),\"LongJmp\") name QName(PackageInternalNs(\"module\"),\"l\") end"
        }) + "\n";
        String res = decompilePCodeSource(pCode, true);
        assertJumpsHaveTargets(res);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*(addr[0-9a-f]+):").matcher(res);
        java.util.Set<String> labels = new java.util.HashSet<>();
        while (m.find()) {
            assertTrue(labels.add(m.group(1)), "label " + m.group(1) + " twice: " + res);
        }
    }
}
