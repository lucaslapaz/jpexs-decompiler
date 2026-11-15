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
import com.jpexs.decompiler.flash.abc.avm2.model.FullMultinameAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetLexAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetSlotAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.IncLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.InitPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.LocalRegAVM2Item;
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
import com.jpexs.decompiler.flash.abc.avm2.model.operations.PreDecrementAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.operations.PreIncrementAVM2Item;
import com.jpexs.decompiler.flash.abc.types.ABCException;
import com.jpexs.decompiler.flash.abc.types.MethodBody;
import com.jpexs.decompiler.flash.abc.types.MethodInfo;
import com.jpexs.decompiler.flash.abc.types.Multiname;
import com.jpexs.decompiler.flash.abc.types.traits.Trait;
import com.jpexs.decompiler.flash.abc.types.traits.TraitMethodGetterSetter;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.graph.AbstractGraphTargetRecursiveVisitor;
import com.jpexs.decompiler.graph.Block;
import com.jpexs.decompiler.graph.GraphTargetItem;
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

    private final Map<Integer, List<String>> valueNames = new LinkedHashMap<>();

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
        Map<Integer, String> ret = new TreeMap<>();
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

        for (int i = 1; i <= paramCount; i++) {
            if (realParamNames || !("param" + i).equals(existingNames.get(i))) {
                continue;
            }
            usedNames.remove("param" + i);
            String name = nameParameter(i, methodInfo);
            ret.put(i, name);
            usedNames.add(name);
        }

        for (int reg : registers) {
            if (reg < firstLocal || existingNames.containsKey(reg)) {
                continue;
            }
            String name = nameLocal(reg);
            ret.put(reg, name);
            usedNames.add(name);
        }
        return ret;
    }

    private String nameParameter(int reg, MethodInfo methodInfo) {
        String typeName = getMultinameName(methodInfo.param_types[reg - 1]);
        if ((trait instanceof TraitMethodGetterSetter) && ((TraitMethodGetterSetter) trait).kindType == Trait.TRAIT_SETTER) {
            return unique("value");
        }
        if (typeName != null && typeName.endsWith("Event")) {
            return unique("event");
        }
        String name = consensus(storeNames.get(reg));
        if (name == null) {
            name = getTypeBaseName(typeName);
        }
        if (name == null) {
            name = "value";
        }
        return unique(name);
    }

    private String nameLocal(int reg) {
        String name = strongNames.get(reg);
        if (name == null) {
            name = consensus(valueNames.get(reg));
        }
        if (name == null) {
            name = consensus(storeNames.get(reg));
        }
        if (name == null && returned.contains(reg)) {
            name = "result";
        }
        if (name == null) {
            name = getTypeBaseName(types.get(reg));
        }
        if (name == null) {
            name = "value";
        }
        return unique(name);
    }

    private String unique(String name) {
        if (!usedNames.contains(name)) {
            return name;
        }
        for (int i = 2;; i++) {
            if (!usedNames.contains(name + i)) {
                return name + i;
            }
        }
    }

    /**
     * Gets the name most of the candidates agree on.
     *
     * @param names Candidates (null when nothing is known)
     * @return Name or null when there are none or they disagree
     */
    private static String consensus(List<String> names) {
        if (names == null) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String n : names) {
            if (n != null) {
                counts.put(n, counts.getOrDefault(n, 0) + 1);
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
        return best;
    }

    private static void add(Map<Integer, List<String>> map, int reg, String name) {
        if (!map.containsKey(reg)) {
            map.put(reg, new ArrayList<>());
        }
        map.get(reg).add(name);
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
                if (reg != -1 && !strongNames.containsKey(reg)) {
                    String collectionName = getValueName(forEach.expression.collection, 0);
                    strongNames.put(reg, collectionName != null ? singular(collectionName) : "item");
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
                add(valueNames, setLocal.regIndex, getValueName(setLocal.value, 0));
            }
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
            if (setSlot.compoundOperator == null && setSlot.slotName != null) {
                addStoreName(setSlot.value, getMultinameName(setSlot.slotName));
            }
        }
        if (item instanceof ReturnValueAVM2Item) {
            int reg = getRegister(unwrap(item.value));
            if (reg != -1) {
                returned.add(reg);
            }
        }
    }

    private void addStoreName(GraphTargetItem value, String name) {
        int reg = getRegister(unwrap(value));
        if (reg != -1) {
            add(storeNames, reg, clean(name));
        }
    }

    private static int getRegister(GraphTargetItem item) {
        if (item instanceof DeclarationAVM2Item) {
            item = ((DeclarationAVM2Item) item).assignment;
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
    private static int getLoopCounter(GraphTargetItem item) {
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

    private static int getIncrementedRegister(GraphTargetItem item) {
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

    private String getValueName(GraphTargetItem value, int depth) {
        if (depth > 5) {
            return null;
        }
        value = unwrap(value);
        if (value instanceof GetPropertyAVM2Item) {
            GetPropertyAVM2Item getProperty = (GetPropertyAVM2Item) value;
            if ((getProperty.propertyName instanceof FullMultinameAVM2Item) && ((FullMultinameAVM2Item) getProperty.propertyName).name != null) {
                //list[i]
                String listName = getValueName(getProperty.object, depth + 1);
                return listName == null ? null : singular(listName);
            }
            return clean(getPropertyName(getProperty.propertyName));
        }
        if (value instanceof GetLexAVM2Item) {
            return clean(getMultinameName(((GetLexAVM2Item) value).propertyName));
        }
        if (value instanceof GetSlotAVM2Item) {
            return clean(getMultinameName(((GetSlotAVM2Item) value).slotName));
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
            return getTypeBaseName(getPropertyName(((ConstructPropAVM2Item) value).propertyName));
        }
        if (value instanceof ConstructAVM2Item) {
            GraphTargetItem object = ((ConstructAVM2Item) value).object;
            if (object instanceof GetLexAVM2Item) {
                return getTypeBaseName(getMultinameName(((GetLexAVM2Item) object).propertyName));
            }
            return null;
        }
        if (value instanceof NewArrayAVM2Item) {
            return "arr";
        }
        if (value instanceof NewObjectAVM2Item) {
            return "obj";
        }
        if (value instanceof NewFunctionAVM2Item) {
            return "func";
        }
        if (value instanceof ThisAVM2Item) {
            return "thisObj";
        }
        if (value instanceof TernarOpItem) {
            TernarOpItem ternar = (TernarOpItem) value;
            String onTrue = getValueName(ternar.onTrue, depth + 1);
            String onFalse = getValueName(ternar.onFalse, depth + 1);
            return onTrue != null && onTrue.equals(onFalse) ? onTrue : null;
        }
        return null;
    }

    private String getCallName(String name, GraphTargetItem receiver, List<GraphTargetItem> arguments, int depth) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (Character.isUpperCase(name.charAt(0))) {
            //String(x), int(x), MyClass(x) are casts
            return arguments != null && arguments.size() == 1 ? getValueName(arguments.get(0), depth + 1) : null;
        }
        switch (name) {
            case "toString":
            case "toLocaleString":
            case "join":
                return "str";
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
                return "index";
            case "split":
                return "parts";
            case "pop":
            case "shift":
            case "removeAt":
                String listName = getValueName(receiver, depth + 1);
                return listName == null ? null : singular(listName);
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
                return getValueName(receiver, depth + 1);
            case "getTimer":
                return "time";
            case "getDefinitionByName":
            case "getDefinition":
                return "definition";
            case "getChildAt":
            case "getChildByName":
            case "removeChild":
            case "removeChildAt":
            case "addChild":
            case "addChildAt":
                return "child";
            case "charCodeAt":
                return "charCode";
            case "random":
                return "random";
        }
        if (receiver instanceof GetLexAVM2Item && "Math".equals(getMultinameName(((GetLexAVM2Item) receiver).propertyName))) {
            return null;
        }
        String[] prefixes = {"get", "create", "make", "build", "find", "compute", "calculate", "calc", "load", "read", "fetch", "generate", "new"};
        for (String prefix : prefixes) {
            if (name.length() > prefix.length() && name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length()))) {
                return clean(name.substring(prefix.length()));
            }
        }
        return clean(name);
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

    static String getTypeBaseName(String typeName) {
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
