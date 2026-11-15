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
package com.jpexs.decompiler.flash.action;

import com.jpexs.decompiler.flash.IdentifiersDeobfuscation;
import com.jpexs.decompiler.flash.abc.avm2.AVM2LocalRegisterNamer;
import com.jpexs.decompiler.flash.action.model.CallFunctionActionItem;
import com.jpexs.decompiler.flash.action.model.CallMethodActionItem;
import com.jpexs.decompiler.flash.action.model.DefineLocalActionItem;
import com.jpexs.decompiler.flash.action.model.DirectValueActionItem;
import com.jpexs.decompiler.flash.action.model.FunctionActionItem;
import com.jpexs.decompiler.flash.action.model.GetMemberActionItem;
import com.jpexs.decompiler.flash.action.model.GetPropertyActionItem;
import com.jpexs.decompiler.flash.action.model.DecrementActionItem;
import com.jpexs.decompiler.flash.action.model.GetVariableActionItem;
import com.jpexs.decompiler.flash.action.model.IncrementActionItem;
import com.jpexs.decompiler.flash.action.model.ToIntegerActionItem;
import com.jpexs.decompiler.flash.action.model.ToNumberActionItem;
import com.jpexs.decompiler.flash.action.model.InitArrayActionItem;
import com.jpexs.decompiler.flash.action.model.InitObjectActionItem;
import com.jpexs.decompiler.flash.action.model.NewObjectActionItem;
import com.jpexs.decompiler.flash.action.model.PostDecrementActionItem;
import com.jpexs.decompiler.flash.action.model.PostIncrementActionItem;
import com.jpexs.decompiler.flash.action.model.ReturnActionItem;
import com.jpexs.decompiler.flash.action.model.SetMemberActionItem;
import com.jpexs.decompiler.flash.action.model.SetPropertyActionItem;
import com.jpexs.decompiler.flash.action.model.SetVariableActionItem;
import com.jpexs.decompiler.flash.action.model.StoreRegisterActionItem;
import com.jpexs.decompiler.flash.action.model.clauses.ForInActionItem;
import com.jpexs.decompiler.flash.action.model.clauses.TellTargetActionItem;
import com.jpexs.decompiler.flash.action.model.clauses.WithActionItem;
import com.jpexs.decompiler.flash.action.model.operations.PreDecrementActionItem;
import com.jpexs.decompiler.flash.action.model.operations.PreIncrementActionItem;
import com.jpexs.decompiler.flash.action.swf4.RegisterNumber;
import com.jpexs.decompiler.graph.AbstractGraphTargetVisitor;
import com.jpexs.decompiler.graph.Block;
import com.jpexs.decompiler.graph.GraphTargetItem;
import com.jpexs.decompiler.graph.model.BinaryOpItem;
import com.jpexs.decompiler.graph.model.DoWhileItem;
import com.jpexs.decompiler.graph.model.ForItem;
import com.jpexs.decompiler.graph.model.TernarOpItem;
import com.jpexs.decompiler.graph.model.WhileItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Names local registers of AS1/2 functions (DefineFunction2) which have no
 * name (_loc1_) from the context of their values, like
 * {@link AVM2LocalRegisterNamer} does for AS3: where the value comes from
 * (var px = this._x gives x), where it goes (this.target = target), loop
 * counters (i, j, k), for in keys, operations and comparisons.
 *
 * <p>
 * A name never hides another one: the variables, functions and parameters
 * named in the function and in its inner functions, reserved words and
 * globals are not used. Up to SWF 6 identifiers are case insensitive, so names
 * are compared ignoring case. Functions with eval, with or tellTarget keep the
 * register names, because there any name can be a variable.
 *
 * @author JPEXS
 */
public class ActionLocalRegisterNamer {

    private static final Set<String> RESERVED = new HashSet<>();

    /**
     * Property names which say little alone: the name of their owner goes
     * before them (this.player._x -> playerX).
     */
    private static final Set<String> GENERIC_PROPERTIES = new HashSet<>(Arrays.asList(
            "x", "y", "z", "width", "height", "length", "name", "value", "data", "id", "type", "index",
            "text", "size", "rotation", "alpha", "xscale", "yscale", "visible", "parent", "currentframe",
            "totalframes", "xmouse", "ymouse", "target", "code", "key", "count", "time", "state", "color",
            "label", "radius", "angle", "speed"));

    private static final String[] LOOP_COUNTER_NAMES = {"i", "j", "k"};

    static {
        for (String w : IdentifiersDeobfuscation.reservedWordsAS2) {
            RESERVED.add(w.toLowerCase(Locale.ENGLISH));
        }
        for (String w : IdentifiersDeobfuscation.reservedWordsAS3) {
            RESERVED.add(w.toLowerCase(Locale.ENGLISH));
        }
        //globals like Math or Key are found by name in the code (GetVariable,
        //CallFunction...), so they are collected with the other names in use
        for (String w : Arrays.asList(
                "void", "enum", "export", "debugger", "let", "yield", "await", "async", "of",
                "arguments", "eval", "undefined", "nan", "infinity", "newline", "this", "super",
                "_root", "_global", "_parent", "_level0", "constructor", "prototype", "__proto__")) {
            RESERVED.add(w);
        }
    }

    private final FunctionActionItem function;

    /**
     * Identifiers are case sensitive from SWF 7.
     */
    private final boolean caseSensitive;

    private final Set<Integer> namedRegisters = new HashSet<>();

    private final Set<String> usedNames = new HashSet<>();

    private final Set<Integer> registers = new TreeSet<>();

    private final Map<Integer, String> strongNames = new LinkedHashMap<>();

    private final Map<Integer, GraphTargetItem> forInObjects = new LinkedHashMap<>();

    private final Map<Integer, List<GraphTargetItem>> valueItems = new LinkedHashMap<>();

    private final Map<Integer, List<String>> storeNames = new LinkedHashMap<>();

    private final Set<Integer> indexRegisters = new HashSet<>();

    private final Set<Integer> returned = new HashSet<>();

    private final Map<Integer, String> assignedNames = new TreeMap<>();

    private boolean dynamicScope = false;

    /**
     * Candidate name.
     */
    private static class Name {

        public final String name;

        public final String alternative;

        public final boolean derived;

        public Name(String name, String alternative, boolean derived) {
            this.name = name;
            this.alternative = alternative;
            this.derived = derived;
        }

        public static Name of(String name) {
            return name == null ? null : new Name(name, null, false);
        }

        public Name derive() {
            return new Name(name, alternative, true);
        }
    }

    /**
     * Constructor.
     *
     * @param function Function
     * @param swfVersion SWF version
     */
    public ActionLocalRegisterNamer(FunctionActionItem function, int swfVersion) {
        this.function = function;
        this.caseSensitive = swfVersion >= 7;
    }

    /**
     * Names the registers without name.
     *
     * @return Map from register number to new name
     */
    public Map<Integer, String> name() {
        if (function.hasEval) {
            return assignedNames;
        }
        walkStatements(function.actions, 0);
        if (dynamicScope) {
            return assignedNames;
        }
        if (function.regNames != null) {
            namedRegisters.addAll(function.regNames.keySet());
            for (String n : function.regNames.values()) {
                use(n);
            }
        }
        if (function.paramNames != null) {
            for (String n : function.paramNames) {
                use(n);
            }
        }
        use(function.functionName);
        collectNames(function.actions, new HashSet<>());

        List<Integer> unnamed = new ArrayList<>();
        for (int reg : registers) {
            if (reg > 0 && !namedRegisters.contains(reg)) {
                unnamed.add(reg);
            }
        }
        for (int round = 1; round <= 2; round++) {
            for (int reg : unnamed) {
                if (assignedNames.containsKey(reg)) {
                    continue;
                }
                String name = nameLocal(reg, round == 2);
                if (name != null) {
                    assignedNames.put(reg, name);
                    use(name);
                }
            }
        }
        return assignedNames;
    }

    private void use(String name) {
        if (name != null) {
            usedNames.add(caseSensitive ? name : name.toLowerCase(Locale.ENGLISH));
        }
    }

    private boolean isUsed(String name) {
        return RESERVED.contains(name.toLowerCase(Locale.ENGLISH)) || usedNames.contains(caseSensitive ? name : name.toLowerCase(Locale.ENGLISH));
    }

    private String nameLocal(int reg, boolean all) {
        List<Name> candidates = new ArrayList<>();
        candidates.add(Name.of(strongNames.get(reg)));
        if (forInObjects.containsKey(reg)) {
            candidates.add(Name.of("key"));
        }
        Name valueName = null;
        if (valueItems.containsKey(reg)) {
            List<Name> names = new ArrayList<>();
            for (GraphTargetItem value : valueItems.get(reg)) {
                names.add(getValueName(value, 0));
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
        if (valueName != null && valueName.derived) {
            if (!isUsed(valueName.name)) {
                return valueName.name;
            }
            if (valueName.alternative != null && !isUsed(valueName.alternative)) {
                return valueName.alternative;
            }
        }
        Name name;
        if (indexRegisters.contains(reg)) {
            name = Name.of("index");
        } else if (returned.contains(reg)) {
            name = Name.of("result");
        } else {
            name = Name.of("value");
        }
        return unique(name);
    }

    private String unique(Name name) {
        if (!isUsed(name.name)) {
            return name.name;
        }
        if (name.alternative != null && !isUsed(name.alternative)) {
            return name.alternative;
        }
        for (int i = 2;; i++) {
            if (!isUsed(name.name + i)) {
                return name.name + i;
            }
        }
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
            return null;
        }
        return firstNames.get(best);
    }

    private static <T> void add(Map<Integer, List<T>> map, int reg, T value) {
        if (!map.containsKey(reg)) {
            map.put(reg, new ArrayList<>());
        }
        map.get(reg).add(value);
    }

    //---------------------------------------------------------------- tree walk

    private void walkStatements(List<GraphTargetItem> items, int loopDepth) {
        if (items == null) {
            return;
        }
        for (GraphTargetItem item : items) {
            if (item == null) {
                continue;
            }
            int counter = getLoopCounter(item);
            if (counter != -1 && !strongNames.containsKey(counter)) {
                strongNames.put(counter, LOOP_COUNTER_NAMES[Math.min(loopDepth, LOOP_COUNTER_NAMES.length - 1)]);
            }
            if (item instanceof ForInActionItem) {
                ForInActionItem forIn = (ForInActionItem) item;
                int reg = getRegister(forIn.variableName);
                if (reg != -1) {
                    forInObjects.put(reg, forIn.enumVariable);
                }
            }
            walkExpression(item);
            if (item instanceof Block) {
                int subDepth = loopDepth + (counter != -1 ? 1 : 0);
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    walkStatements(sub, subDepth);
                }
            }
        }
    }

    private void walkExpression(GraphTargetItem item) {
        if (item == null) {
            return;
        }
        visitExpression(item);
        //inner functions have their own registers: FunctionActionItem.visitNoBlock is empty
        item.visitNoBlock(new AbstractGraphTargetVisitor() {
            @Override
            public boolean visit(GraphTargetItem subItem) {
                walkExpression(subItem);
                return false;
            }
        });
    }

    private void visitExpression(GraphTargetItem item) {
        if ((item instanceof WithActionItem) || (item instanceof TellTargetActionItem)) {
            dynamicScope = true;
        }
        //set("a" + i, v), eval(...): variables with computed names
        if ((item instanceof GetVariableActionItem) && getString(((GetVariableActionItem) item).name) == null) {
            dynamicScope = true;
        }
        if ((item instanceof SetVariableActionItem) && getString(((SetVariableActionItem) item).name) == null) {
            dynamicScope = true;
        }
        if ((item instanceof DefineLocalActionItem) && getString(((DefineLocalActionItem) item).name) == null) {
            dynamicScope = true;
        }
        if ((item instanceof CallFunctionActionItem) && getString(((CallFunctionActionItem) item).functionName) == null) {
            dynamicScope = true;
        }
        if (item instanceof DirectValueActionItem && ((DirectValueActionItem) item).value instanceof RegisterNumber) {
            registers.add(((RegisterNumber) ((DirectValueActionItem) item).value).number);
        }
        if (item instanceof StoreRegisterActionItem) {
            StoreRegisterActionItem store = (StoreRegisterActionItem) item;
            registers.add(store.register.number);
            if (store.compoundOperator == null && store.value != null && !store.temporary) {
                add(valueItems, store.register.number, store.value);
            }
        }
        if (item instanceof SetMemberActionItem) {
            SetMemberActionItem setMember = (SetMemberActionItem) item;
            if (setMember.compoundOperator == null) {
                addStoreName(setMember.value, getString(setMember.objectName));
            }
            addIndexRegister(setMember.objectName);
        }
        if (item instanceof GetMemberActionItem) {
            addIndexRegister(((GetMemberActionItem) item).memberName);
        }
        if (item instanceof SetPropertyActionItem) {
            SetPropertyActionItem setProperty = (SetPropertyActionItem) item;
            addStoreName(setProperty.value, getPropertyName(setProperty.propertyIndex));
        }
        if (item instanceof SetVariableActionItem) {
            SetVariableActionItem setVariable = (SetVariableActionItem) item;
            if (setVariable.compoundOperator == null) {
                addStoreName(setVariable.value, getString(setVariable.name));
            }
        }
        if (item instanceof DefineLocalActionItem) {
            DefineLocalActionItem defineLocal = (DefineLocalActionItem) item;
            addStoreName(defineLocal.value, getString(defineLocal.name));
        }
        if (item instanceof ReturnActionItem) {
            int reg = getRegister(item.value);
            if (reg != -1) {
                returned.add(reg);
            }
        }
    }

    private void addIndexRegister(GraphTargetItem memberName) {
        int reg = getRegister(memberName);
        if (reg != -1) {
            indexRegisters.add(reg);
        }
    }

    private void addStoreName(GraphTargetItem value, String name) {
        int reg = getRegister(value);
        if (reg != -1) {
            add(storeNames, reg, AVM2LocalRegisterNamer.clean(name));
        }
    }

    private static int getRegister(GraphTargetItem item) {
        if (item instanceof DirectValueActionItem && ((DirectValueActionItem) item).value instanceof RegisterNumber) {
            return ((RegisterNumber) ((DirectValueActionItem) item).value).number;
        }
        if (item instanceof StoreRegisterActionItem) {
            return ((StoreRegisterActionItem) item).register.number;
        }
        return -1;
    }

    private static String getString(GraphTargetItem item) {
        if (item instanceof DirectValueActionItem) {
            return ((DirectValueActionItem) item).getAsString();
        }
        return null;
    }

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
        if (getRegister(comparison.leftSide) == reg || getRegister(comparison.rightSide) == reg) {
            return reg;
        }
        return -1;
    }

    private static int getIncrementedRegister(GraphTargetItem item) {
        if (item instanceof PostIncrementActionItem) {
            return getRegister(((PostIncrementActionItem) item).object);
        }
        if (item instanceof PostDecrementActionItem) {
            return getRegister(((PostDecrementActionItem) item).object);
        }
        if ((item instanceof PreIncrementActionItem) || (item instanceof PreDecrementActionItem)) {
            return getRegister(item.value);
        }
        if (item instanceof StoreRegisterActionItem) {
            StoreRegisterActionItem store = (StoreRegisterActionItem) item;
            if ("+".equals(store.compoundOperator) || "-".equals(store.compoundOperator)) {
                return store.register.number;
            }
        }
        return -1;
    }

    //---------------------------------------------------------------- names in use

    /**
     * Collects names of variables, functions and parameters of the function
     * and its inner functions.
     *
     * @param items Items
     * @param visited Visited items
     */
    private void collectNames(List<GraphTargetItem> items, Set<GraphTargetItem> visited) {
        if (items == null) {
            return;
        }
        for (GraphTargetItem item : items) {
            if (item == null) {
                continue;
            }
            collectName(item);
            item.visitRecursively(new AbstractGraphTargetVisitor() {
                @Override
                public boolean visit(GraphTargetItem subItem) {
                    if (subItem != null) {
                        collectName(subItem);
                    }
                    return true;
                }
            });
        }
    }

    private void collectName(GraphTargetItem item) {
        if (item instanceof GetVariableActionItem) {
            use(getString(((GetVariableActionItem) item).name));
        }
        if (item instanceof SetVariableActionItem) {
            use(getString(((SetVariableActionItem) item).name));
        }
        if (item instanceof DefineLocalActionItem) {
            use(getString(((DefineLocalActionItem) item).name));
        }
        if (item instanceof CallFunctionActionItem) {
            use(getString(((CallFunctionActionItem) item).functionName));
        }
        if (item instanceof NewObjectActionItem) {
            use(getString(((NewObjectActionItem) item).objectName));
        }
        if (item instanceof FunctionActionItem) {
            FunctionActionItem inner = (FunctionActionItem) item;
            use(inner.functionName);
            if (inner.paramNames != null) {
                for (String n : inner.paramNames) {
                    use(n);
                }
            }
            if (inner.regNames != null) {
                for (String n : inner.regNames.values()) {
                    use(n);
                }
            }
        }
        if (item instanceof ForInActionItem) {
            use(getString(((ForInActionItem) item).variableName));
        }
    }

    //---------------------------------------------------------------- names from values

    private static boolean isLiteral(GraphTargetItem item) {
        return (item instanceof DirectValueActionItem) && !(((DirectValueActionItem) item).value instanceof RegisterNumber);
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String join(String owner, String name) {
        if (owner == null || name == null) {
            return null;
        }
        return AVM2LocalRegisterNamer.clean(owner + capitalize(name));
    }

    private static String clean(String name) {
        String ret = AVM2LocalRegisterNamer.clean(name);
        if (ret == null || RESERVED.contains(ret.toLowerCase(Locale.ENGLISH)) || !IdentifiersDeobfuscation.isValidName(false, ret)) {
            return null;
        }
        return ret;
    }

    private Name getOperandName(GraphTargetItem item, int depth, boolean locals) {
        int reg = getRegister(item);
        if (reg != -1 && !(item instanceof StoreRegisterActionItem)) {
            if (!locals) {
                return null;
            }
            String name = assignedNames.containsKey(reg) ? assignedNames.get(reg) : (function.regNames == null ? null : function.regNames.get(reg));
            if (isPreloaded(name)) {
                return null;
            }
            return AVM2LocalRegisterNamer.isStrong(name) ? Name.of(name) : null;
        }
        if (isLiteral(item)) {
            return null;
        }
        return getValueName(item, depth + 1);
    }

    /**
     * Checks for names of the registers DefineFunction2 preloads and of the
     * timeline globals, which are not about a value.
     *
     * @param name Name
     * @return True if it is one of them
     */
    private static boolean isPreloaded(String name) {
        return "this".equals(name) || "_root".equals(name) || "_parent".equals(name) || "_global".equals(name)
                || "arguments".equals(name) || "super".equals(name);
    }

    private String getOwnerName(GraphTargetItem object, int depth) {
        if (object instanceof GetVariableActionItem) {
            String variable = getString(((GetVariableActionItem) object).name);
            if ("this".equals(variable) || "_root".equals(variable) || "_parent".equals(variable) || "_global".equals(variable)) {
                return null;
            }
        }
        if (object instanceof DirectValueActionItem && ((DirectValueActionItem) object).value instanceof RegisterNumber) {
            String registerName = function.regNames == null ? null : function.regNames.get(((RegisterNumber) ((DirectValueActionItem) object).value).number);
            if ("this".equals(registerName) || "_root".equals(registerName) || "_parent".equals(registerName) || "_global".equals(registerName)) {
                return null;
            }
        }
        Name name = getOperandName(object, depth, true);
        if (name == null || !AVM2LocalRegisterNamer.isStrong(name.name)) {
            return null;
        }
        return name.name;
    }

    private static String getPropertyName(int propertyIndex) {
        if (propertyIndex < 0 || propertyIndex >= Action.propertyNames.length) {
            return null;
        }
        return Action.propertyNames[propertyIndex];
    }

    private static boolean isEmptyTarget(GraphTargetItem target) {
        if (target == null) {
            return true;
        }
        String targetName = getString(target);
        return targetName != null && targetName.isEmpty();
    }

    private String getPropertyTail(GraphTargetItem item) {
        if (item instanceof GetPropertyActionItem) {
            String property = clean(getPropertyName(((GetPropertyActionItem) item).propertyIndex));
            return property == null ? null : property.toLowerCase(Locale.ENGLISH);
        }
        if (item instanceof GetMemberActionItem) {
            String member = clean(getString(((GetMemberActionItem) item).memberName));
            return member == null ? null : member.toLowerCase(Locale.ENGLISH);
        }
        int reg = getRegister(item);
        if (reg != -1) {
            String name = assignedNames.get(reg);
            if (name != null && name.length() > 1) {
                char last = name.charAt(name.length() - 1);
                if (last == 'X' || last == 'Y') {
                    return String.valueOf(Character.toLowerCase(last));
                }
            }
        }
        return null;
    }

    private Name getValueName(GraphTargetItem value, int depth) {
        if (depth > 6 || value == null) {
            return null;
        }
        if (value instanceof GetMemberActionItem) {
            GetMemberActionItem getMember = (GetMemberActionItem) value;
            String member = getString(getMember.memberName);
            if (member == null) {
                //list[i]
                Name list = getOperandName(getMember.object, depth, true);
                return list == null ? null : Name.of(AVM2LocalRegisterNamer.singular(list.name));
            }
            String property = clean(member);
            if (property == null) {
                return null;
            }
            String qualified = join(getOwnerName(getMember.object, depth), property);
            if (qualified != null && GENERIC_PROPERTIES.contains(property.toLowerCase(Locale.ENGLISH))) {
                return new Name(qualified, null, false);
            }
            return new Name(property, qualified, false);
        }
        if (value instanceof GetPropertyActionItem) {
            //this._x, getProperty(target, _x)
            GetPropertyActionItem getProperty = (GetPropertyActionItem) value;
            String property = clean(getPropertyName(getProperty.propertyIndex));
            if (property == null) {
                return null;
            }
            String owner = isEmptyTarget(getProperty.target) ? null : getOwnerName(getProperty.target, depth);
            String qualified = join(owner, property);
            if (qualified != null && GENERIC_PROPERTIES.contains(property.toLowerCase(Locale.ENGLISH))) {
                return new Name(qualified, null, false);
            }
            return new Name(property, qualified, false);
        }
        if (value instanceof GetVariableActionItem) {
            String variable = getString(((GetVariableActionItem) value).name);
            return isPreloaded(variable) ? null : Name.of(clean(variable));
        }
        if ((value instanceof ToIntegerActionItem) || (value instanceof ToNumberActionItem)) {
            return getOperandName(value.value, depth, true);
        }
        if ((value instanceof IncrementActionItem) || (value instanceof DecrementActionItem)) {
            //a + 1
            GraphTargetItem object = (value instanceof IncrementActionItem) ? ((IncrementActionItem) value).object : ((DecrementActionItem) value).object;
            Name name = getOperandName(object, depth, false);
            return name == null ? null : name.derive();
        }
        if (value instanceof CallMethodActionItem) {
            CallMethodActionItem call = (CallMethodActionItem) value;
            return getCallName(getString(call.methodName), call.scriptObject, call.arguments, depth);
        }
        if (value instanceof CallFunctionActionItem) {
            CallFunctionActionItem call = (CallFunctionActionItem) value;
            return getCallName(getString(call.functionName), null, call.arguments, depth);
        }
        if (value instanceof NewObjectActionItem) {
            String type = getString(((NewObjectActionItem) value).objectName);
            return Name.of(type == null ? null : clean(AVM2LocalRegisterNamer.getTypeBaseName(type)));
        }
        if (value instanceof InitArrayActionItem) {
            return Name.of("arr");
        }
        if (value instanceof InitObjectActionItem) {
            return Name.of("obj");
        }
        if (value instanceof FunctionActionItem) {
            return Name.of("func");
        }
        if (value instanceof TernarOpItem) {
            TernarOpItem ternar = (TernarOpItem) value;
            Name onTrue = getOperandName(ternar.onTrue, depth, true);
            Name onFalse = getOperandName(ternar.onFalse, depth, true);
            if (onTrue != null && onFalse != null) {
                return onTrue.name.equals(onFalse.name) ? onTrue : null;
            }
            if (onTrue != null && isLiteral(ternar.onFalse)) {
                return onTrue.derive();
            }
            if (onFalse != null && isLiteral(ternar.onTrue)) {
                return onFalse.derive();
            }
            if (isLiteral(ternar.onTrue) && isLiteral(ternar.onFalse)) {
                Name condition = getOperandName(ternar.expression, depth, false);
                return condition == null ? null : condition.derive();
            }
            return null;
        }
        if (value instanceof BinaryOpItem) {
            return getOperationName((BinaryOpItem) value, depth);
        }
        return null;
    }

    private Name getOperationName(BinaryOpItem operation, int depth) {
        String operator = operation.getOperator();
        GraphTargetItem left = operation.leftSide;
        GraphTargetItem right = operation.rightSide;
        switch (operator) {
            case "-":
                String leftTail = getPropertyTail(left);
                if (leftTail != null && leftTail.equals(getPropertyTail(right)) && (leftTail.equals("x") || leftTail.equals("y"))) {
                    return Name.of("d" + leftTail).derive();
                }
            //fallthrough
            case "+":
            case "add":
            case "*":
            case "/":
            case "%":
            case "&":
            case "|":
            case "^":
            case "<<":
            case ">>":
            case ">>>":
            case "&&":
            case "||":
            case "and":
            case "or":
                Name name = getOperandName(left, depth, false);
                return name == null ? null : name.derive();
            case "==":
            case "===":
            case "!=":
            case "!==":
            case "eq":
            case "ne":
                boolean equal = operator.startsWith("=") || operator.equals("eq");
                GraphTargetItem other = null;
                if (isNullOrUndefined(right)) {
                    other = left;
                } else if (isNullOrUndefined(left)) {
                    other = right;
                }
                if (other != null) {
                    return prefixed(equal ? "no" : "has", getOperandName(other, depth, true));
                }
                if (!equal) {
                    return null;
                }
                return prefixed("is", getOperandName(left, depth, true));
        }
        return null;
    }

    private static boolean isNullOrUndefined(GraphTargetItem item) {
        if (!(item instanceof DirectValueActionItem)) {
            return false;
        }
        Object v = ((DirectValueActionItem) item).value;
        return v == com.jpexs.decompiler.flash.ecma.Null.INSTANCE || v == com.jpexs.decompiler.flash.ecma.Undefined.INSTANCE;
    }

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
        String lower = name.toLowerCase(Locale.ENGLISH);
        if (lower.equals("int") || lower.equals("number") || lower.equals("string") || lower.equals("boolean")
                || lower.equals("parseint") || lower.equals("parsefloat")) {
            return arguments != null && arguments.size() >= 1 ? getOperandName(arguments.get(0), depth, true) : null;
        }
        boolean math = (receiver instanceof GetVariableActionItem) && "math".equalsIgnoreCase(getString(((GetVariableActionItem) receiver).name));
        if (math) {
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
            case "getTimer":
                return Name.of("time");
            case "random":
                return Name.of("rnd");
            case "toString":
            case "join":
                return Name.of("str");
            case "indexOf":
            case "lastIndexOf":
                return Name.of("index");
            case "split":
                return Name.of("parts");
            case "pop":
            case "shift":
                Name listName = getOperandName(receiver, depth, true);
                return listName == null ? null : Name.of(AVM2LocalRegisterNamer.singular(listName.name));
            case "concat":
            case "slice":
            case "splice":
            case "substr":
            case "substring":
            case "toLowerCase":
            case "toUpperCase":
            case "charAt":
                return getOperandName(receiver, depth, true);
            case "attachMovie":
            case "createEmptyMovieClip":
            case "duplicateMovieClip":
                return Name.of("mc");
            case "charCodeAt":
                return Name.of("charCode");
            case "valueOf":
            case "apply":
            case "call":
            case "hasOwnProperty":
                return null;
        }
        String[] prefixes = {"get", "create", "make", "build", "find", "compute", "calculate", "calc", "load", "read", "fetch", "generate", "new"};
        for (String prefix : prefixes) {
            if (name.length() > prefix.length() && name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length()))) {
                return Name.of(clean(name.substring(prefix.length())));
            }
        }
        return Name.of(clean(Character.toLowerCase(name.charAt(0)) + name.substring(1)));
    }
}
