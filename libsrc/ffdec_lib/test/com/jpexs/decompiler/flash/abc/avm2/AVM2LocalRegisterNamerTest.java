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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import org.testng.annotations.Test;

/**
 * Names made from property, method and type names.
 *
 * @author JPEXS
 */
public class AVM2LocalRegisterNamerTest {

    @Test
    public void testClean() {
        assertEquals(AVM2LocalRegisterNamer.clean("TextField"), "textField");
        assertEquals(AVM2LocalRegisterNamer.clean("URLLoader"), "urlLoader");
        assertEquals(AVM2LocalRegisterNamer.clean("MAX_SPEED"), "maxSpeed");
        assertEquals(AVM2LocalRegisterNamer.clean("ID"), "id");
        assertEquals(AVM2LocalRegisterNamer.clean("_target"), "target");
        assertEquals(AVM2LocalRegisterNamer.clean("$Joueur"), "joueur");
        assertEquals(AVM2LocalRegisterNamer.clean("m_userData"), "m_userData");
        assertNull(AVM2LocalRegisterNamer.clean("default"), "reserved word");
        assertNull(AVM2LocalRegisterNamer.clean("Namespace"), "namespace is a keyword");
        assertNull(AVM2LocalRegisterNamer.clean("§_-9c§"), "obfuscated name");
        assertNull(AVM2LocalRegisterNamer.clean("___"));
        assertNull(AVM2LocalRegisterNamer.clean(null));
    }

    @Test
    public void testSingular() {
        assertEquals(AVM2LocalRegisterNamer.singular("enemies"), "enemy");
        assertEquals(AVM2LocalRegisterNamer.singular("boxes"), "box");
        assertEquals(AVM2LocalRegisterNamer.singular("players"), "player");
        assertEquals(AVM2LocalRegisterNamer.singular("glass"), "glassItem");
        assertEquals(AVM2LocalRegisterNamer.singular("playerList"), "player");
        assertEquals(AVM2LocalRegisterNamer.singular("data"), "dataItem");
    }

    @Test
    public void testTypeBaseName() {
        assertEquals(AVM2LocalRegisterNamer.getTypeBaseName("int"), "num");
        assertEquals(AVM2LocalRegisterNamer.getTypeBaseName("String"), "str");
        assertEquals(AVM2LocalRegisterNamer.getTypeBaseName("MouseEvent"), "event");
        assertEquals(AVM2LocalRegisterNamer.getTypeBaseName("TypeError"), "error");
        assertEquals(AVM2LocalRegisterNamer.getTypeBaseName("MovieClip"), "movieClip");
        assertEquals(AVM2LocalRegisterNamer.getTypeBaseName("b2Body"), "body", "lowercase prefix of the type is dropped");
        assertEquals(AVM2LocalRegisterNamer.getTypeBaseName("point"), "pointObj", "never the type name itself");
        assertNull(AVM2LocalRegisterNamer.getTypeBaseName("*"));
    }
}
