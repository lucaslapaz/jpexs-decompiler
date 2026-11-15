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

import com.jpexs.decompiler.flash.IdentifiersDeobfuscation;
import com.jpexs.decompiler.flash.abc.ABC;
import com.jpexs.decompiler.flash.abc.avm2.instructions.AVM2Instruction;
import com.jpexs.decompiler.flash.abc.avm2.instructions.AVM2Instructions;
import com.jpexs.decompiler.flash.abc.avm2.model.ApplyTypeAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.CallMethodAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.CallPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.CallSuperAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.CoerceAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ConstructAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ConstructPropAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ConvertAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.DecLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.FindPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.FullMultinameAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetLexAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetSlotAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.IncLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.InitPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.LocalRegAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NullAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.UndefinedAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NewArrayAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NewFunctionAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NewObjectAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.PostDecrementAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.PostIncrementAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ReturnValueAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetSlotAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ThisAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.WithAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.clauses.DeclarationAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.clauses.ForEachInAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.clauses.ForInAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.AsTypeAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.IsTypeAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.NegAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.PreDecrementAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.PreIncrementAVM2Item;
import com.jpexs.decompiler.flash.abc.types.ABCException;
import com.jpexs.decompiler.flash.abc.types.MethodBody;
import com.jpexs.decompiler.flash.abc.types.MethodInfo;
import com.jpexs.decompiler.flash.abc.types.Multiname;
import com.jpexs.decompiler.flash.abc.types.traits.Trait;
import com.jpexs.decompiler.flash.abc.types.traits.TraitMethodGetterSetter;
import com.jpexs.decompiler.flash.abc.types.traits.TraitSlotConst;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.graph.AbstractGraphTargetRecursiveVisitor;
import com.jpexs.decompiler.graph.Block;
import com.jpexs.decompiler.graph.GraphTargetItem;
import com.jpexs.decompiler.graph.SimpleValue;
import com.jpexs.decompiler.graph.TypeItem;
import com.jpexs.decompiler.graph.model.BinaryOpItem;
import com.jpexs.decompiler.graph.model.DoWhileItem;
import com.jpexs.decompiler.graph.model.ForItem;
import com.jpexs.decompiler.graph.model.TernarOpItem;
import com.jpexs.decompiler.graph.model.WhileItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * Names local registers which have no name (_loc1_, param1...) from the context
 * of their values: where the value comes from (var length:int = list.length),
 * where it goes (this.target = target), loop counters (i, j, k), for in keys,
 * for each items (the singular of the collection) and, when nothing else is
 * known, the type (var point:Point).
 *
 * <p>
 * A name never hides another one: names looked up in the scope by the method
 * or its inner functions (findpropstrict, getlex, finddef...), types, slots,
 * package names and reserved words are not used. Methods with a with
 * statement keep the register names, because there any name can be a
 * property of the with object.
 *
 * @author JPEXS
 */
public class AVM2LocalRegisterNamer {

    /**
     * Names which are never used: reserved words of AS3 and of ECMAScript
     * (the code may be converted to JavaScript), and globals that the
     * decompiler writes without looking them up.
     */
    private static final Set<String> RESERVED = new HashSet<>();

    /**
     * Opcodes which name a property of an explicit object. Other multinames
     * are scope lookups and types.
     */
    private static final Set<Integer> PROPERTY_OF_OBJECT_OPCODES = new HashSet<>(Arrays.asList(
            AVM2Instructions.GetProperty, AVM2Instructions.SetProperty, AVM2Instructions.InitProperty,
            AVM2Instructions.CallProperty, AVM2Instructions.CallPropVoid, AVM2Instructions.CallPropLex,
            AVM2Instructions.ConstructProp, AVM2Instructions.DeleteProperty, AVM2Instructions.GetSuper,
            AVM2Instructions.SetSuper, AVM2Instructions.CallSuper, AVM2Instructions.CallSuperVoid,
            AVM2Instructions.GetDescendants));

    /**
     * First segments of all package names of an ABC.
     */
    private static final Map<ABC, Set<String>> PACKAGE_ROOTS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final String[] LOOP_COUNTER_NAMES = {"i", "j", "k"};

    /**
     * Property names which say little alone: the name of their owner goes
     * before them (this.player.x -> playerX).
     */
    private static final Set<String> GENERIC_PROPERTIES = new HashSet<>(Arrays.asList(
            "x", "y", "z", "width", "height", "length", "name", "value", "data", "id", "type", "index",
            "text", "size", "rotation", "alpha", "scaleX", "scaleY", "visible", "parent", "content",
            "numChildren", "mouseX", "mouseY", "left", "right", "top", "bottom", "code", "key", "count",
            "time", "state", "color", "currentFrame", "totalFrames", "label", "radius", "angle"));

    /**
     * Names from the type only, which say nothing about the use.
     */
    private static final Set<String> WEAK_NAMES = new HashSet<>(Arrays.asList(
            "num", "str", "flag", "obj", "value", "arr", "vec", "func", "cls", "xml", "xmlList", "dict",
            "bytes", "ns", "item", "result", "thisObj"));

    static {
        RESERVED.addAll(Arrays.asList(IdentifiersDeobfuscation.reservedWordsAS3));
        RESERVED.addAll(Arrays.asList(IdentifiersDeobfuscation.reservedWordsAS3_2));
        RESERVED.addAll(Arrays.asList(IdentifiersDeobfuscation.syntacticKeywordsAS3));
        RESERVED.addAll(Arrays.asList(
                "void", "to", "enum", "export", "debugger", "let", "yield", "await", "async", "of", "type",
                "arguments", "eval", "undefined", "NaN", "Infinity", "global", "rest", "self",
                "int", "uint", "Number", "String", "Boolean", "Object", "Array", "Function", "Class",
                "XML", "XMLList", "Vector", "Namespace", "QName", "Math", "trace", "constructor", "prototype"));
    }

    private final ABC abc;

    private final MethodBody body;

    private final Trait trait;

    private final Map<Integer, String> existingNames;

    private final Set<String> usedNames = new HashSet<>();

    private final Set<Integer> registers = new TreeSet<>();

    private final Map<Integer, String> strongNames = new LinkedHashMap<>();

    private final Map<Integer, GraphTargetItem> forEachCollections = new LinkedHashMap<>();

    private final Map<Integer, List<GraphTargetItem>> valueItems = new LinkedHashMap<>();

    private final Set<Integer> indexRegisters = new HashSet<>();

    private final Map<Integer, String> assignedNames = new TreeMap<>();

    /**
     * Activation slots with the name of a register (_loc2_, param1) by slot id:
     * code compiled again from decompiled code (the FFDec compiler keeps the
     * names, and puts the locals to slots when the method needs an
     * activation).
     */
    private final Map<Integer, String> candidateSlots = new LinkedHashMap<>();

    private final Map<Integer, String> slotTypeNames = new LinkedHashMap<>();

    private final Map<String, String> slotNameOverrides = new LinkedHashMap<>();

    /**
     * Key of a slot in the maps of registers.
     *
     * @param slotId Slot id
     * @return Key
     */
    private static int slotKey(int slotId) {
        return -1000 - slotId;
    }

    private static boolean isSlotKey(int key) {
        return key <= -1000;
    }

    private final Map<Integer, List<String>> storeNames = new LinkedHashMap<>();

    private final Map<Integer, GraphTargetItem> types = new LinkedHashMap<>();

    private final Set<Integer> returned = new HashSet<>();

    private boolean hasWith = false;

    /**
     * Constructor.
     *
     * @param abc ABC
     * @param body Method body (bytecode for the names in use)
     * @param trait Trait of the method or null
     * @param existingNames Names of registers which have one (parameters,
     * debug info...)
     */
    public AVM2LocalRegisterNamer(ABC abc, MethodBody body, Trait trait, Map<Integer, String> existingNames) {
        this.abc = abc;
        this.body = body;
        this.trait = trait;
        this.existingNames = existingNames;
    }

    /**
     * Names the registers.
     *
     * @param items Method body items
     * @return Map from register index to new name
     */
    public Map<Integer, String> name(List<GraphTargetItem> items) {
        Map<Integer, String> ret = assignedNames;
        collectCandidateSlots();
        walkStatements(items, 0);
        if (hasWith) {
            return ret;
        }

        usedNames.addAll(RESERVED);
        usedNames.addAll(existingNames.values());
        usedNames.addAll(getPackageRoots(abc));
        collectNamesInUse(body, new HashSet<>());

        MethodInfo methodInfo = abc.method_info.get(body.method_info);
        int paramCount = methodInfo.param_types.length;
        boolean realParamNames = methodInfo.flagHas_paramnames() && Configuration.paramNamesEnable.get();
        int firstLocal = paramCount + 1 + (methodInfo.flagNeed_arguments() ? 1 : 0) + (methodInfo.flagNeed_rest() ? 1 : 0);

        //A parameter copied to an activation slot of the same name is that slot:
        //the decompiler leaves the copy out, so the name must stay
        Set<String> slotNames = new HashSet<>();
        if (body.traits != null) {
            for (Trait t : body.traits.traits) {
                String slotName = getMultinameName(t.name_index);
                if (slotName != null) {
                    slotNames.add(slotName);
                }
            }
        }

        //slots with a register name: a parameter copied to slot paramN gets the name of the slot
        for (Map.Entry<Integer, String> slot : candidateSlots.entrySet()) {
            int key = slotKey(slot.getKey());
            String slotName = slot.getValue();
            usedNames.remove(slotName);
            String name = null;
            if (slotName.matches("param[0-9]+")) {
                int param = Integer.parseInt(slotName.substring(5));
                if (param >= 1 && param <= paramCount && ("param" + param).equals(existingNames.get(param)) && !realParamNames) {
                    String typeName = getMultinameName(methodInfo.param_types[param - 1]);
                    if ((trait instanceof TraitMethodGetterSetter) && ((TraitMethodGetterSetter) trait).kindType == Trait.TRAIT_SETTER) {
                        name = unique(Name.of("value"));
                    } else if (typeName != null && typeName.endsWith("Event")) {
                        name = unique(Name.of("event"));
                    } else {
                        name = nameLocal(key, true);
                        //a parameter which is returned is not a result
                        if (name.replaceAll("[0-9]+$", "").equals("result")) {
                            usedNames.remove(name);
                            String typeBase = getTypeBaseName(typeName);
                            name = unique(Name.of(typeBase == null ? "value" : typeBase));
                        }
                    }
                    ret.put(param, name);
                }
            }
            if (name == null) {
                name = nameLocal(key, true);
            }
            slotNameOverrides.put(slotName, name);
            usedNames.add(name);
        }

        for (int i = 1; i <= paramCount; i++) {
            if (realParamNames || !("param" + i).equals(existingNames.get(i)) || slotNames.contains("param" + i)) {
                continue;
            }
            usedNames.remove("param" + i);
            String name = nameParameter(i, methodInfo);
            ret.put(i, name);
            usedNames.add(name);
        }

        //first the locals named by where their value comes from or goes to,
        //then the ones named by operations, which can use the first ones
        List<Integer> locals = new ArrayList<>();
        for (int reg : registers) {
            if (reg < firstLocal || existingNames.containsKey(reg) || slotNames.contains(String.format(Configuration.registerNameFormat.get(), reg))) {
                continue;
            }
            locals.add(reg);
        }
        for (int round = 1; round <= 2; round++) {
            for (int reg : locals) {
                if (ret.containsKey(reg)) {
                    continue;
                }
                String name = nameLocal(reg, round == 2);
                if (name != null) {
                    ret.put(reg, name);
                    usedNames.add(name);
                }
            }
        }
        Map<Integer, String> registerNames = new TreeMap<>();
        for (Map.Entry<Integer, String> entry : ret.entrySet()) {
            if (entry.getKey() >= 0) {
                registerNames.put(entry.getKey(), entry.getValue());
            }
        }
        return registerNames;
    }

    /**
     * Gets new names of the activation slots which had the name of a
     * register. Valid after {@link #name(java.util.List)}.
     *
     * @return Map from slot name to new name
     */
    public Map<String, String> getSlotNameOverrides() {
        return slotNameOverrides;
    }

    private void collectCandidateSlots() {
        if (body.traits == null) {
            return;
        }
        //inner functions read the slots by name
        for (AVM2Instruction ins : body.getCode().code) {
            if (ins.definition.instructionCode == AVM2Instructions.NewFunction) {
                return;
            }
        }
        String registerPattern = Pattern.quote(Configuration.registerNameFormat.get()).replace("%d", "\\E[0-9]+\\Q");
        for (Trait t : body.traits.traits) {
            if (!(t instanceof TraitSlotConst)) {
                continue;
            }
            String slotName = getMultinameName(t.name_index);
            if (slotName == null) {
                continue;
            }
            if (slotName.matches("param[0-9]+") || slotName.matches(registerPattern)) {
                TraitSlotConst slot = (TraitSlotConst) t;
                candidateSlots.put(slot.slot_id, slotName);
                slotTypeNames.put(slot.slot_id, getMultinameName(slot.type_index));
            }
        }
    }

    /**
     * Candidate name.
     */
    private static class Name {

        /**
         * Name
         */
        public final String name;

        /**
         * Name to use before a number when the name is taken, or null
         */
        public final String alternative;

        /**
         * Made from an operation (a - b, a == null...), not from where the
         * value comes from
         */
        public final boolean derived;

        /**
         * Name of an arithmetic operation made from all its terms, when the
         * name and the alternative are taken (aPlus3PlusX), after index and
         * result, or null
         */
        public final String fallback;

        public Name(String name, String alternative, boolean derived) {
            this(name, alternative, derived, null);
        }

        public Name(String name, String alternative, boolean derived, String fallback) {
            this.name = name;
            this.alternative = alternative;
            this.derived = derived;
            this.fallback = fallback;
        }

        public static Name of(String name) {
            return name == null ? null : new Name(name, null, false);
        }

        public Name derive() {
            return new Name(name, alternative, true, fallback);
        }
    }

    private String nameParameter(int reg, MethodInfo methodInfo) {
        String typeName = getMultinameName(methodInfo.param_types[reg - 1]);
        if ((trait instanceof TraitMethodGetterSetter) && ((TraitMethodGetterSetter) trait).kindType == Trait.TRAIT_SETTER) {
            return unique(Name.of("value"));
        }
        if (typeName != null && typeName.endsWith("Event")) {
            return unique(Name.of("event"));
        }
        Name name = consensus(toNames(storeNames.get(reg)));
        if (name == null) {
            name = Name.of(getTypeBaseName(typeName));
        }
        if (name == null) {
            name = Name.of("value");
        }
        return unique(name);
    }

    private static List<Name> toNames(List<String> names) {
        if (names == null) {
            return null;
        }
        List<Name> ret = new ArrayList<>();
        for (String n : names) {
            ret.add(Name.of(n));
        }
        return ret;
    }

    /**
     * Names a local.
     *
     * @param reg Register
     * @param all False for the first round: only names from where the value
     * comes from or goes to
     * @return Name or null when it waits for the second round
     */
    private String nameLocal(int reg, boolean all) {
        GraphTargetItem type = types.get(reg);
        boolean stringType = (type instanceof TypeItem) && TypeItem.STRING.equals(type);
        List<Name> candidates = new ArrayList<>();
        candidates.add(Name.of(strongNames.get(reg)));
        if (forEachCollections.containsKey(reg)) {
            Name collection = getValueName(forEachCollections.get(reg), 0);
            candidates.add(collection == null ? Name.of("item") : new Name(singular(collection.name), collection.alternative == null ? null : singular(collection.alternative), false));
        }
        Name valueName = null;
        if (valueItems.containsKey(reg)) {
            List<Name> names = new ArrayList<>();
            for (GraphTargetItem value : valueItems.get(reg)) {
                Name name = getValueName(value, 0);
                //"a" + b is not b
                if (name != null && name.derived && stringType) {
                    name = null;
                }
                names.add(name);
            }
            valueName = consensus(names);
        }
        if (valueName != null && !valueName.derived) {
            candidates.add(valueName);
        }
        candidates.add(consensus(toNames(storeNames.get(reg))));
        for (Name candidate : candidates) {
            if (candidate != null) {
                return unique(candidate);
            }
        }
        if (!all) {
            return null;
        }
        //a name made from an operation is only used when it is free:
        //speed2 = speed * 2 would look like another speed
        if (valueName != null && valueName.derived) {
            if (!usedNames.contains(valueName.name)) {
                return valueName.name;
            }
            if (valueName.alternative != null && !usedNames.contains(valueName.alternative)) {
                return valueName.alternative;
            }
        }
        Name name = null;
        if (indexRegisters.contains(reg)) {
            name = Name.of(stringType ? "key" : "index");
        } else if (returned.contains(reg)) {
            name = Name.of("result");
        }
        if (name == null && valueName != null && valueName.fallback != null && !usedNames.contains(valueName.fallback)) {
            return valueName.fallback;
        }
        if (name == null) {
            name = Name.of(getTypeBaseName(type));
        }
        if (name == null && isSlotKey(reg)) {
            name = Name.of(getTypeBaseName(slotTypeNames.get(-1000 - reg)));
        }
        if (name == null) {
            name = Name.of("value");
        }
        return unique(name);
    }

    private String unique(Name name) {
        if (!usedNames.contains(name.name)) {
            return name.name;
        }
        if (name.alternative != null && !usedNames.contains(name.alternative)) {
            return name.alternative;
        }
        for (int i = 2;; i++) {
            if (!usedNames.contains(name.name + i)) {
                return name.name + i;
            }
        }
    }

    /**
     * Gets the name most of the candidates agree on.
     *
     * @param names Candidates (null when nothing is known)
     * @return Name or null when there are none or they disagree
     */
    private static Name consensus(List<Name> names) {
        if (names == null) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Name> firstNames = new LinkedHashMap<>();
        for (Name n : names) {
            if (n != null) {
                counts.put(n.name, counts.getOrDefault(n.name, 0) + 1);
                if (!firstNames.containsKey(n.name)) {
                    firstNames.put(n.name, n);
                }
            }
        }
        if (counts.isEmpty()) {
            return null;
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        if (counts.size() > 1 && bestCount == 1) {
            //each assignment says something else, the register is probably reused
            return null;
        }
        return firstNames.get(best);
    }

    /**
     * Checks whether a name says something about the use.
     *
     * @param name Name
     * @return True if it does
     */
    public static boolean isStrong(String name) {
        if (name == null) {
            return false;
        }
        String base = name.replaceAll("[0-9]+$", "");
        if (WEAK_NAMES.contains(base) || base.isEmpty()) {
            return false;
        }
        if (name.matches("param[0-9]+")) {
            return false;
        }
        return !name.equals(String.format(Configuration.registerNameFormat.get(), getRegisterOfDefaultName(name)));
    }

    private static int getRegisterOfDefaultName(String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]+").matcher(name);
        return m.find() ? Integer.parseInt(m.group()) : -1;
    }

    private static <T> void add(Map<Integer, List<T>> map, int reg, T value) {
        if (!map.containsKey(reg)) {
            map.put(reg, new ArrayList<>());
        }
        map.get(reg).add(value);
    }

    //---------------------------------------------------------------- tree walk

    private void walkStatements(List<GraphTargetItem> items, int loopDepth) {
        for (GraphTargetItem item : items) {
            if (item == null) {
                continue;
            }
            int counter = getLoopCounter(item);
            if (counter != -1 && !strongNames.containsKey(counter)) {
                strongNames.put(counter, LOOP_COUNTER_NAMES[Math.min(loopDepth, LOOP_COUNTER_NAMES.length - 1)]);
            }
            if (item instanceof ForEachInAVM2Item) {
                ForEachInAVM2Item forEach = (ForEachInAVM2Item) item;
                int reg = getRegister(forEach.expression.object);
                if (reg != -1 && !strongNames.containsKey(reg) && !forEachCollections.containsKey(reg)) {
                    forEachCollections.put(reg, forEach.expression.collection);
                }
            }
            if (item instanceof ForInAVM2Item) {
                ForInAVM2Item forIn = (ForInAVM2Item) item;
                int reg = getRegister(forIn.expression.object);
                if (reg != -1 && !strongNames.containsKey(reg)) {
                    strongNames.put(reg, "key");
                }
            }
            visitExpression(item);
            item.visitRecursivelyNoBlock(new AbstractGraphTargetRecursiveVisitor() {
                @Override
                public void visit(GraphTargetItem subItem, Stack<GraphTargetItem> parentStack) {
                    visitExpression(subItem);
                }
            });
            if (item instanceof Block) {
                int subDepth = loopDepth + (counter != -1 ? 1 : 0);
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    walkStatements(sub, subDepth);
                }
            }
        }
    }

    private void visitExpression(GraphTargetItem item) {
        if (item instanceof WithAVM2Item) {
            hasWith = true;
        }
        if (item instanceof LocalRegAVM2Item) {
            LocalRegAVM2Item localReg = (LocalRegAVM2Item) item;
            registers.add(localReg.regIndex);
            if (!types.containsKey(localReg.regIndex) && localReg.type != null) {
                types.put(localReg.regIndex, localReg.type);
            }
        }
        if (item instanceof IncLocalAVM2Item) {
            registers.add(((IncLocalAVM2Item) item).regIndex);
        }
        if (item instanceof DecLocalAVM2Item) {
            registers.add(((DecLocalAVM2Item) item).regIndex);
        }
        if (item instanceof DeclarationAVM2Item) {
            DeclarationAVM2Item declaration = (DeclarationAVM2Item) item;
            int reg = getRegister(declaration.assignment);
            if (reg != -1 && declaration.type != null) {
                types.put(reg, declaration.type);
            }
        }
        if (item instanceof SetLocalAVM2Item) {
            SetLocalAVM2Item setLocal = (SetLocalAVM2Item) item;
            registers.add(setLocal.regIndex);
            if (setLocal.compoundOperator == null && setLocal.value != null) {
                add(valueItems, setLocal.regIndex, setLocal.value);
            }
        }
        if (item instanceof GetPropertyAVM2Item) {
            addIndexRegister(((GetPropertyAVM2Item) item).propertyName);
        }
        if (item instanceof SetPropertyAVM2Item) {
            addIndexRegister(((SetPropertyAVM2Item) item).propertyName);
        }
        if (item instanceof SetPropertyAVM2Item) {
            SetPropertyAVM2Item setProperty = (SetPropertyAVM2Item) item;
            if (setProperty.compoundOperator == null) {
                addStoreName(setProperty.value, getPropertyName(setProperty.propertyName));
            }
        }
        if (item instanceof InitPropertyAVM2Item) {
            InitPropertyAVM2Item initProperty = (InitPropertyAVM2Item) item;
            if (initProperty.compoundOperator == null) {
                addStoreName(initProperty.value, getPropertyName(initProperty.propertyName));
            }
        }
        if (item instanceof SetSlotAVM2Item) {
            SetSlotAVM2Item setSlot = (SetSlotAVM2Item) item;
            int slot = getRegister(setSlot);
            if (slot != -1) {
                //a local in a slot
                registers.add(slot);
                if (setSlot.compoundOperator == null && setSlot.value != null) {
                    add(valueItems, slot, setSlot.value);
                }
            } else if (setSlot.compoundOperator == null && setSlot.slotName != null) {
                addStoreName(setSlot.value, getMultinameName(setSlot.slotName));
            }
        }
        if ((item instanceof GetSlotAVM2Item) && getRegister(item) != -1) {
            registers.add(getRegister(item));
        }
        if (item instanceof ReturnValueAVM2Item) {
            int reg = getRegister(unwrap(item.value));
            if (reg != -1) {
                returned.add(reg);
            }
        }
    }

    private void addIndexRegister(GraphTargetItem propertyName) {
        if ((propertyName instanceof FullMultinameAVM2Item) && ((FullMultinameAVM2Item) propertyName).name != null) {
            int reg = getRegister(unwrap(((FullMultinameAVM2Item) propertyName).name));
            if (reg != -1) {
                indexRegisters.add(reg);
            }
        }
    }

    private void addStoreName(GraphTargetItem value, String name) {
        int reg = getRegister(unwrap(value));
        if (reg != -1) {
            add(storeNames, reg, clean(name));
        }
    }

    private int getRegister(GraphTargetItem item) {
        if (item instanceof DeclarationAVM2Item) {
            item = ((DeclarationAVM2Item) item).assignment;
        }
        if ((item instanceof SetSlotAVM2Item) && candidateSlots.containsKey(((SetSlotAVM2Item) item).slotIndex)) {
            return slotKey(((SetSlotAVM2Item) item).slotIndex);
        }
        if ((item instanceof GetSlotAVM2Item) && candidateSlots.containsKey(((GetSlotAVM2Item) item).slotIndex)) {
            return slotKey(((GetSlotAVM2Item) item).slotIndex);
        }
        if (item instanceof LocalRegAVM2Item) {
            return ((LocalRegAVM2Item) item).regIndex;
        }
        if (item instanceof SetLocalAVM2Item) {
            return ((SetLocalAVM2Item) item).regIndex;
        }
        return -1;
    }

    /**
     * Gets the counter of a loop: a register incremented or decremented at the
     * end of each iteration and compared in the condition.
     *
     * @param item Statement
     * @return Register or -1
     */
    private int getLoopCounter(GraphTargetItem item) {
        List<GraphTargetItem> step;
        GraphTargetItem condition;
        if (item instanceof ForItem) {
            ForItem forItem = (ForItem) item;
            step = forItem.finalCommands;
            condition = forItem.expression;
        } else if (item instanceof WhileItem) {
            WhileItem whileItem = (WhileItem) item;
            step = whileItem.commands;
            condition = whileItem.expression == null || whileItem.expression.isEmpty() ? null : whileItem.expression.get(whileItem.expression.size() - 1);
        } else if (item instanceof DoWhileItem) {
            DoWhileItem doWhileItem = (DoWhileItem) item;
            step = doWhileItem.commands;
            condition = doWhileItem.expression == null || doWhileItem.expression.isEmpty() ? null : doWhileItem.expression.get(doWhileItem.expression.size() - 1);
        } else {
            return -1;
        }
        if (step == null || step.isEmpty() || !(condition instanceof BinaryOpItem)) {
            return -1;
        }
        int reg = getIncrementedRegister(step.get(step.size() - 1));
        if (reg == -1) {
            return -1;
        }
        BinaryOpItem comparison = (BinaryOpItem) condition;
        if (getRegister(unwrap(comparison.leftSide)) == reg || getRegister(unwrap(comparison.rightSide)) == reg) {
            return reg;
        }
        return -1;
    }

    private int getIncrementedRegister(GraphTargetItem item) {
        if (item instanceof IncLocalAVM2Item) {
            return ((IncLocalAVM2Item) item).regIndex;
        }
        if (item instanceof DecLocalAVM2Item) {
            return ((DecLocalAVM2Item) item).regIndex;
        }
        if (item instanceof PostIncrementAVM2Item) {
            return getRegister(((PostIncrementAVM2Item) item).object);
        }
        if (item instanceof PostDecrementAVM2Item) {
            return getRegister(((PostDecrementAVM2Item) item).object);
        }
        if ((item instanceof PreIncrementAVM2Item) || (item instanceof PreDecrementAVM2Item)) {
            return getRegister(item.value);
        }
        if (item instanceof SetLocalAVM2Item) {
            SetLocalAVM2Item setLocal = (SetLocalAVM2Item) item;
            if ("+".equals(setLocal.compoundOperator) || "-".equals(setLocal.compoundOperator)) {
                return setLocal.regIndex;
            }
        }
        return -1;
    }

    //---------------------------------------------------------------- names from values

    private static GraphTargetItem unwrap(GraphTargetItem item) {
        while (true) {
            if ((item instanceof CoerceAVM2Item) || (item instanceof ConvertAVM2Item)) {
                item = item.value;
            } else if (item instanceof AsTypeAVM2Item) {
                item = ((AsTypeAVM2Item) item).leftSide;
            } else {
                return item;
            }
        }
    }

    private static boolean isLiteral(GraphTargetItem item) {
        item = unwrap(item);
        return (item instanceof SimpleValue) && ((SimpleValue) item).isSimpleValue();
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Joins an owner and a property name: player + x = playerX.
     *
     * @param owner Owner name or null
     * @param name Name
     * @return Joined name or null
     */
    private static String join(String owner, String name) {
        if (owner == null || name == null) {
            return null;
        }
        String ret = owner + capitalize(name);
        return IdentifiersDeobfuscation.isValidName(true, ret) && !RESERVED.contains(ret) ? ret : null;
    }

    /**
     * Gets name of an operand: the name of a local or of where the value comes
     * from.
     *
     * @param item Operand
     * @param depth Depth
     * @return Name or null
     */
    private Name getOperandName(GraphTargetItem item, int depth) {
        return getOperandName(item, depth, true);
    }

    /**
     * Gets name of an operand.
     *
     * @param item Operand
     * @param depth Depth
     * @param locals Use the names of locals too (an operation on a local is
     * not the local: seconds = time % 60)
     * @return Name or null
     */
    private Name getOperandName(GraphTargetItem item, int depth, boolean locals) {
        item = unwrap(item);
        if ((item instanceof GetSlotAVM2Item) && getRegister(item) != -1) {
            if (!locals) {
                return null;
            }
            String name = assignedNames.get(getRegister(item));
            if (name == null) {
                name = slotNameOverrides.get(candidateSlots.get(-1000 - getRegister(item)));
            }
            return isStrong(name) ? Name.of(name) : null;
        }
        if (item instanceof LocalRegAVM2Item) {
            if (!locals) {
                return null;
            }
            int reg = ((LocalRegAVM2Item) item).regIndex;
            String name = assignedNames.containsKey(reg) ? assignedNames.get(reg) : existingNames.get(reg);
            return isStrong(name) ? Name.of(name) : null;
        }
        if (isLiteral(item)) {
            return null;
        }
        return getValueName(item, depth + 1);
    }

    /**
     * Gets name of the owner of a property: player in this.player.x.
     *
     * @param object Object
     * @param depth Depth
     * @return Name or null (this has no name)
     */
    private String getOwnerName(GraphTargetItem object, int depth) {
        Name name = getOperandName(object, depth);
        if (name == null || !isStrong(name.name)) {
            return null;
        }
        return name.name;
    }

    /**
     * Gets name of the last property of a value (x in this.player.x).
     *
     * @param item Value
     * @return Name or null
     */
    private String getPropertyTail(GraphTargetItem item) {
        item = unwrap(item);
        if (item instanceof GetPropertyAVM2Item) {
            return getPropertyName(((GetPropertyAVM2Item) item).propertyName);
        }
        if (item instanceof LocalRegAVM2Item) {
            //a local named playerX
            String name = assignedNames.get(((LocalRegAVM2Item) item).regIndex);
            if (name != null && name.length() > 1) {
                char last = name.charAt(name.length() - 1);
                if (last == 'X' || last == 'Y' || last == 'Z') {
                    return String.valueOf(Character.toLowerCase(last));
                }
            }
        }
        return null;
    }

    private Name getValueName(GraphTargetItem value, int depth) {
        if (depth > 6) {
            return null;
        }
        value = unwrap(value);
        if (value instanceof GetPropertyAVM2Item) {
            GetPropertyAVM2Item getProperty = (GetPropertyAVM2Item) value;
            if ((getProperty.propertyName instanceof FullMultinameAVM2Item) && ((FullMultinameAVM2Item) getProperty.propertyName).name != null) {
                //list[i]
                Name list = getOperandName(getProperty.object, depth);
                return list == null ? null : Name.of(singular(list.name));
            }
            String property = clean(getPropertyName(getProperty.propertyName));
            if (property == null) {
                return null;
            }
            String qualified = join(getOwnerName(getProperty.object, depth), property);
            if (qualified != null && GENERIC_PROPERTIES.contains(property)) {
                return new Name(qualified, null, false);
            }
            return new Name(property, qualified, false);
        }
        if (value instanceof GetLexAVM2Item) {
            return Name.of(clean(getMultinameName(((GetLexAVM2Item) value).propertyName)));
        }
        if (value instanceof GetSlotAVM2Item) {
            if (getRegister(value) != -1) {
                return null;
            }
            return Name.of(clean(getMultinameName(((GetSlotAVM2Item) value).slotName)));
        }
        if (value instanceof CallPropertyAVM2Item) {
            CallPropertyAVM2Item call = (CallPropertyAVM2Item) value;
            return getCallName(getPropertyName(call.propertyName), call.receiver, call.arguments, depth);
        }
        if (value instanceof CallMethodAVM2Item) {
            CallMethodAVM2Item call = (CallMethodAVM2Item) value;
            return getCallName(call.methodName, call.receiver, call.arguments, depth);
        }
        if (value instanceof CallSuperAVM2Item) {
            CallSuperAVM2Item call = (CallSuperAVM2Item) value;
            return getCallName(getPropertyName(call.multiname), call.receiver, call.arguments, depth);
        }
        if (value instanceof ConstructPropAVM2Item) {
            return Name.of(getTypeBaseName(getPropertyName(((ConstructPropAVM2Item) value).propertyName)));
        }
        if (value instanceof ConstructAVM2Item) {
            GraphTargetItem object = ((ConstructAVM2Item) value).object;
            if (object instanceof GetLexAVM2Item) {
                return Name.of(getTypeBaseName(getMultinameName(((GetLexAVM2Item) object).propertyName)));
            }
            return null;
        }
        if (value instanceof NewArrayAVM2Item) {
            return Name.of("arr");
        }
        if (value instanceof NewObjectAVM2Item) {
            return Name.of("obj");
        }
        if (value instanceof NewFunctionAVM2Item) {
            return Name.of("func");
        }
        if (value instanceof ThisAVM2Item) {
            return Name.of("thisObj");
        }
        if (value instanceof TernarOpItem) {
            TernarOpItem ternar = (TernarOpItem) value;
            Name onTrue = getOperandName(ternar.onTrue, depth);
            Name onFalse = getOperandName(ternar.onFalse, depth);
            if (onTrue != null && onFalse != null) {
                return onTrue.name.equals(onFalse.name) ? onTrue : null;
            }
            //a ? 0.5 : b
            if (onTrue != null && isLiteral(ternar.onFalse)) {
                return onTrue.derive();
            }
            if (onFalse != null && isLiteral(ternar.onTrue)) {
                return onFalse.derive();
            }
            //a.visible ? 1 : 0
            if (isLiteral(ternar.onTrue) && isLiteral(ternar.onFalse)) {
                Name condition = getOperandName(ternar.expression, depth, false);
                return condition == null ? null : condition.derive();
            }
            return null;
        }
        if (value instanceof NegAVM2Item) {
            Name name = getOperandName(value.value, depth, false);
            return name == null ? null : name.derive();
        }
        if (value instanceof IsTypeAVM2Item) {
            String typeName = getTypeName(((IsTypeAVM2Item) value).rightSide);
            String ret = typeName == null ? null : clean("is" + capitalize(typeName));
            return Name.of(ret) == null ? null : Name.of(ret).derive();
        }
        if (value instanceof BinaryOpItem) {
            return getOperationName((BinaryOpItem) value, depth);
        }
        return null;
    }

    private String getTypeName(GraphTargetItem type) {
        type = unwrap(type);
        if (type instanceof GetLexAVM2Item) {
            return getMultinameName(((GetLexAVM2Item) type).propertyName);
        }
        if (type instanceof TypeItem) {
            return ((TypeItem) type).fullTypeName.getLast();
        }
        return null;
    }

    private Name getOperationName(BinaryOpItem operation, int depth) {
        String operator = operation.getOperator();
        GraphTargetItem left = operation.leftSide;
        GraphTargetItem right = operation.rightSide;
        switch (operator) {
            case "-":
                //a.x - b.x -> dx
                String leftTail = getPropertyTail(left);
                if (leftTail != null && leftTail.equals(getPropertyTail(right)) && (leftTail.equals("x") || leftTail.equals("y") || leftTail.equals("z"))) {
                    return Name.of("d" + leftTail).derive();
                }
            //fallthrough
            case "+":
                return getArithmeticName(operation, depth);
            case "*":
            case "/":
            case "%":
                return getArithmeticName(operation, depth);
            case "&":
            case "|":
            case "^":
            case "<<":
            case ">>":
            case ">>>":
            case "&&":
            case "||":
                //only the leftmost operand: a + b * c is about a; when it is a local
                //(seconds = time % 60) or a literal, the operation has no name
                Name name = getOperandName(left, depth, false);
                return name == null ? null : name.derive();
            case "==":
            case "===":
            case "!=":
            case "!==":
                boolean equal = operator.startsWith("=");
                GraphTargetItem other = null;
                if (unwrap(right) instanceof NullAVM2Item || unwrap(right) instanceof UndefinedAVM2Item) {
                    other = left;
                } else if (unwrap(left) instanceof NullAVM2Item || unwrap(left) instanceof UndefinedAVM2Item) {
                    other = right;
                }
                if (other != null) {
                    //a != null -> hasA
                    return prefixed(equal ? "no" : "has", getOperandName(other, depth));
                }
                if (!equal) {
                    return null;
                }
                return prefixed("is", getOperandName(left, depth));
        }
        return null;
    }

    /**
     * Name of an arithmetic operation: the name of the leftmost operand, and,
     * for when it is in use, a name made from all the terms (a + 3 + x:
     * aPlus3PlusX; this.x + this.parent.x: xPlusParentX). x2 would look like
     * another x, and the name of the type says less.
     *
     * @param operation Operation
     * @param depth Depth
     * @return Name or null
     */
    private Name getArithmeticName(BinaryOpItem operation, int depth) {
        Name name = getOperandName(operation.leftSide, depth, false);
        if (name == null) {
            return null;
        }
        return new Name(name.name, name.alternative, true, getTermsName(operation, depth));
    }

    /**
     * Name made from the terms of a chain of operations of the same kind
     * (a + 3 - x, a * b / 2), at most three terms, each one a name or a small
     * whole number; at least one of them a name.
     *
     * @param operation Operation
     * @param depth Depth
     * @return Name or null
     */
    private String getTermsName(BinaryOpItem operation, int depth) {
        String family = getOperatorFamily(operation.getOperator());
        if (family == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        GraphTargetItem item = operation;
        //a + b + c is (a + b) + c: from the right to the left
        while ((item instanceof BinaryOpItem) && family.equals(getOperatorFamily(((BinaryOpItem) item).getOperator()))) {
            BinaryOpItem op = (BinaryOpItem) item;
            String term = getTermName(op.rightSide, depth);
            if (term == null) {
                return null;
            }
            parts.add(0, capitalize(term));
            parts.add(0, getOperatorWord(op.getOperator()));
            item = op.leftSide;
        }
        String first = getTermName(item, depth);
        if (first == null || parts.size() > 4) {
            return null;
        }
        boolean named = !Character.isDigit(first.charAt(0));
        for (int i = 1; i < parts.size(); i += 2) {
            named = named || !Character.isDigit(parts.get(i).charAt(0));
        }
        if (!named || Character.isDigit(first.charAt(0))) {
            return null;
        }
        return clean(first + String.join("", parts));
    }

    /**
     * Name of a term: the name of the operand (of a local too) or a small whole
     * number.
     *
     * @param item Term
     * @param depth Depth
     * @return Name or null
     */
    private String getTermName(GraphTargetItem item, int depth) {
        if (isLiteral(item)) {
            Object value = unwrap(item).getResult();
            if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (number >= 0 && number < 10000 && number == Math.floor(number)) {
                    return String.valueOf((long) number);
                }
            }
            return null;
        }
        if (item instanceof BinaryOpItem) {
            return null;
        }
        Name name = getOperandName(item, depth, true);
        return name == null ? null : name.name;
    }

    private static String getOperatorFamily(String operator) {
        switch (operator) {
            case "+":
            case "add":
            case "-":
                return "+";
            case "*":
            case "/":
            case "%":
                return "*";
        }
        return null;
    }

    private static String getOperatorWord(String operator) {
        switch (operator) {
            case "-":
                return "Minus";
            case "*":
                return "Times";
            case "/":
                return "Over";
            case "%":
                return "Mod";
        }
        return "Plus";
    }

    /**
     * Name with a prefix (has + player = hasPlayer), the owner included when
     * known (hasTp1Stage).
     *
     * @param prefix Prefix
     * @param name Name or null
     * @return Name or null
     */
    private static Name prefixed(String prefix, Name name) {
        if (name == null) {
            return null;
        }
        String alternative = name.alternative == null ? null : clean(prefix + capitalize(name.alternative));
        String main = clean(prefix + capitalize(name.name));
        if (alternative != null) {
            return new Name(alternative, main, true);
        }
        return main == null ? null : new Name(main, null, true);
    }

    private Name getCallName(String name, GraphTargetItem receiver, List<GraphTargetItem> arguments, int depth) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        boolean global = receiver == null || (receiver instanceof FindPropertyAVM2Item) || (receiver instanceof GetLexAVM2Item);
        if (name.equals("int") || name.equals("uint") || (global && Character.isUpperCase(name.charAt(0)))) {
            //String(x), int(x), MyClass(x) are casts
            if (arguments != null && arguments.size() == 1) {
                Name argumentName = getOperandName(arguments.get(0), depth);
                if (argumentName != null || name.equals("int") || name.equals("uint")) {
                    return argumentName;
                }
            }
            //FindMaxSeparation(a, b) is a function
            if (name.equals("int") || name.equals("uint")) {
                return null;
            }
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        } else if (Character.isUpperCase(name.charAt(0))) {
            //this.Trouver_Jeu(x) is a method
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        if (receiver instanceof GetLexAVM2Item && "Math".equals(getMultinameName(((GetLexAVM2Item) receiver).propertyName))) {
            switch (name) {
                case "atan2":
                case "atan":
                case "acos":
                case "asin":
                    return Name.of("angle");
                case "sqrt":
                    return Name.of("dist");
                case "sin":
                case "cos":
                case "tan":
                case "random":
                    return Name.of(name);
                case "abs":
                case "floor":
                case "ceil":
                case "round":
                case "min":
                case "max":
                case "pow":
                    if (arguments != null) {
                        for (GraphTargetItem argument : arguments) {
                            Name argumentName = getOperandName(argument, depth, false);
                            if (argumentName != null) {
                                return argumentName.derive();
                            }
                        }
                    }
                    return null;
            }
            return null;
        }
        switch (name) {
            case "toString":
            case "toLocaleString":
            case "join":
                return Name.of("str");
            case "valueOf":
            case "apply":
            case "call":
            case "hasOwnProperty":
            case "toFixed":
            case "toPrecision":
            case "toExponential":
                return null;
            case "indexOf":
            case "lastIndexOf":
            case "search":
            case "getChildIndex":
                return Name.of("index");
            case "split":
                return Name.of("parts");
            case "pop":
            case "shift":
            case "removeAt":
                Name listName = getOperandName(receiver, depth);
                return listName == null ? null : Name.of(singular(listName.name));
            case "concat":
            case "slice":
            case "splice":
            case "filter":
            case "map":
            case "sort":
            case "sortOn":
            case "reverse":
            case "clone":
            case "copy":
            case "substr":
            case "substring":
            case "toLowerCase":
            case "toUpperCase":
            case "replace":
            case "charAt":
                return getOperandName(receiver, depth);
            case "getTimer":
                return Name.of("time");
            case "getDefinitionByName":
            case "getDefinition":
                return Name.of("definition");
            case "getChildAt":
            case "getChildByName":
            case "removeChild":
            case "removeChildAt":
            case "addChild":
            case "addChildAt":
                return Name.of("child");
            case "charCodeAt":
                return Name.of("charCode");
        }
        String[] prefixes = {"get", "create", "make", "build", "find", "compute", "calculate", "calc", "load", "read", "fetch", "generate", "new"};
        for (String prefix : prefixes) {
            if (name.length() > prefix.length() && name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length()))) {
                return Name.of(clean(name.substring(prefix.length())));
            }
        }
        //decodeURI(x) gives decodeURI, the name of the function itself: decodeURIResult, not decodeURI2,
        //which looks like another function
        return resultOf(clean(name));
    }

    private static Name resultOf(String name) {
        return name == null ? null : new Name(name, name + "Result", false);
    }

    private String getPropertyName(GraphTargetItem propertyName) {
        if (!(propertyName instanceof FullMultinameAVM2Item)) {
            return null;
        }
        FullMultinameAVM2Item fullMultiname = (FullMultinameAVM2Item) propertyName;
        if (fullMultiname.name != null) {
            return null;
        }
        if (fullMultiname.resolvedMultinameName != null) {
            return fullMultiname.resolvedMultinameName;
        }
        if (fullMultiname.multinameIndex > 0 && fullMultiname.multinameIndex < abc.constants.getMultinameCount()) {
            return getMultinameName(abc.constants.getMultiname(fullMultiname.multinameIndex));
        }
        return null;
    }

    private String getMultinameName(int multinameIndex) {
        if (multinameIndex <= 0 || multinameIndex >= abc.constants.getMultinameCount()) {
            return null;
        }
        return getMultinameName(abc.constants.getMultiname(multinameIndex));
    }

    private String getMultinameName(Multiname multiname) {
        if (multiname == null) {
            return null;
        }
        if (multiname.kind == Multiname.TYPENAME) {
            return getMultinameName(multiname.qname_index);
        }
        if (multiname.name_index <= 0 || multiname.name_index >= abc.constants.getStringCount()) {
            return null;
        }
        return abc.constants.getString(multiname.name_index);
    }

    //---------------------------------------------------------------- names from types

    private String getTypeBaseName(GraphTargetItem type) {
        if (type instanceof ApplyTypeAVM2Item) {
            return "vec";
        }
        if (!(type instanceof TypeItem)) {
            return null;
        }
        return getTypeBaseName(((TypeItem) type).fullTypeName.getLast());
    }

    public static String getTypeBaseName(String typeName) {
        if (typeName == null || typeName.isEmpty() || "*".equals(typeName)) {
            return null;
        }
        switch (typeName) {
            case "int":
            case "uint":
            case "Number":
                return "num";
            case "String":
                return "str";
            case "Boolean":
                return "flag";
            case "Object":
                return "obj";
            case "Array":
                return "arr";
            case "Vector":
                return "vec";
            case "Function":
                return "func";
            case "Class":
                return "cls";
            case "XML":
                return "xml";
            case "XMLList":
                return "xmlList";
            case "Dictionary":
                return "dict";
            case "ByteArray":
                return "bytes";
            case "Namespace":
                return "ns";
        }
        if (typeName.endsWith("Event")) {
            return "event";
        }
        if (typeName.endsWith("Error")) {
            return "error";
        }
        //b2Body -> body
        int start = 0;
        while (start < typeName.length() && !Character.isUpperCase(typeName.charAt(start))) {
            start++;
        }
        String name = start < typeName.length() && start > 0 ? typeName.substring(start) : typeName;
        String ret = clean(name);
        if (ret == null) {
            return "obj";
        }
        if (ret.equals(typeName)) {
            ret = ret + "Obj";
        }
        return ret;
    }

    /**
     * Makes a local name from a property, method or type name.
     *
     * @param name Name
     * @return Local name or null when it cannot be used
     */
    public static String clean(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (!IdentifiersDeobfuscation.isValidName(true, name)) {
            return null;
        }
        int start = 0;
        while (start < name.length() && (name.charAt(start) == '_' || name.charAt(start) == '$')) {
            start++;
        }
        int end = name.length();
        while (end > start && name.charAt(end - 1) == '_') {
            end--;
        }
        if (start >= end) {
            return null;
        }
        name = name.substring(start, end);
        if (!Character.isLetter(name.charAt(0))) {
            return null;
        }

        boolean constant = true;
        boolean hasLetter = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    constant = false;
                }
            } else if (c != '_' && !Character.isDigit(c)) {
                constant = false;
            }
        }
        String ret;
        if (constant && hasLetter && name.length() > 1) {
            //MAX_SPEED -> maxSpeed
            StringBuilder sb = new StringBuilder();
            boolean upper = false;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '_') {
                    upper = sb.length() > 0;
                    continue;
                }
                sb.append(upper ? c : Character.toLowerCase(c));
                upper = false;
            }
            ret = sb.toString();
        } else {
            //TextField -> textField, URLLoader -> urlLoader
            int upperCount = 0;
            while (upperCount < name.length() && Character.isUpperCase(name.charAt(upperCount))) {
                upperCount++;
            }
            if (upperCount == 0) {
                ret = name;
            } else if (upperCount == 1 || upperCount == name.length()) {
                ret = name.substring(0, upperCount).toLowerCase() + name.substring(upperCount);
            } else if (Character.isLetter(name.charAt(upperCount))) {
                ret = name.substring(0, upperCount - 1).toLowerCase() + name.substring(upperCount - 1);
            } else {
                ret = name.substring(0, upperCount).toLowerCase() + name.substring(upperCount);
            }
        }
        if (ret.isEmpty() || RESERVED.contains(ret) || !IdentifiersDeobfuscation.isValidName(true, ret)) {
            return null;
        }
        return ret;
    }

    /**
     * Gets name of one item of a list.
     *
     * @param name Name of the list
     * @return Name of an item
     */
    public static String singular(String name) {
        if (name.length() > 3 && name.endsWith("ies")) {
            return name.substring(0, name.length() - 3) + "y";
        }
        if (name.length() > 4 && (name.endsWith("sses") || name.endsWith("xes") || name.endsWith("ches") || name.endsWith("shes"))) {
            return name.substring(0, name.length() - 2);
        }
        if (name.length() > 2 && name.endsWith("s") && !name.endsWith("ss")) {
            return name.substring(0, name.length() - 1);
        }
        if (name.length() > 4 && name.endsWith("List")) {
            return name.substring(0, name.length() - 4);
        }
        return name + "Item";
    }

    //---------------------------------------------------------------- names in use

    private static Set<String> getPackageRoots(ABC abc) {
        Set<String> ret = PACKAGE_ROOTS.get(abc);
        if (ret != null) {
            return ret;
        }
        ret = new HashSet<>();
        for (int i = 1; i < abc.constants.getNamespaceCount(); i++) {
            try {
                String name = abc.constants.getNamespace(i).getRawName(abc.constants);
                if (name != null && !name.isEmpty()) {
                    int dot = name.indexOf('.');
                    ret.add(dot == -1 ? name : name.substring(0, dot));
                }
            } catch (Exception ex) {
                //invalid namespace
            }
        }
        PACKAGE_ROOTS.put(abc, ret);
        return ret;
    }

    /**
     * Collects names the code of the method and of its inner functions can
     * see: everything it looks up in the scope, its types, slots and
     * exceptions.
     *
     * @param methodBody Method body
     * @param visitedBodies Visited bodies
     */
    private void collectNamesInUse(MethodBody methodBody, Set<MethodBody> visitedBodies) {
        if (methodBody == null || visitedBodies.contains(methodBody)) {
            return;
        }
        visitedBodies.add(methodBody);
        MethodInfo methodInfo = abc.method_info.get(methodBody.method_info);
        for (int paramType : methodInfo.param_types) {
            addMultinameInUse(paramType);
        }
        addMultinameInUse(methodInfo.ret_type);
        if (methodBody.traits != null) {
            for (Trait t : methodBody.traits.traits) {
                addMultinameInUse(t.name_index);
            }
        }
        if (methodBody.exceptions != null) {
            for (ABCException exception : methodBody.exceptions) {
                addMultinameInUse(exception.name_index);
                addMultinameInUse(exception.type_index);
            }
        }
        for (AVM2Instruction ins : methodBody.getCode().code) {
            int code = ins.definition.instructionCode;
            if (code == AVM2Instructions.NewFunction && ins.operands.length > 0) {
                collectNamesInUse(abc.findBody(ins.operands[0]), visitedBodies);
                continue;
            }
            if (PROPERTY_OF_OBJECT_OPCODES.contains(code)) {
                continue;
            }
            for (int i = 0; i < ins.definition.operands.length && i < ins.operands.length; i++) {
                if (ins.definition.operands[i] == AVM2Code.DAT_MULTINAME_INDEX) {
                    addMultinameInUse(ins.operands[i]);
                }
            }
        }
    }

    private void addMultinameInUse(int multinameIndex) {
        if (multinameIndex <= 0 || multinameIndex >= abc.constants.getMultinameCount()) {
            return;
        }
        Multiname multiname = abc.constants.getMultiname(multinameIndex);
        if (multiname.kind == Multiname.TYPENAME) {
            addMultinameInUse(multiname.qname_index);
            if (multiname.params != null) {
                for (int param : multiname.params) {
                    addMultinameInUse(param);
                }
            }
            return;
        }
        String name = getMultinameName(multiname);
        if (name != null) {
            usedNames.add(name);
        }
    }
}
