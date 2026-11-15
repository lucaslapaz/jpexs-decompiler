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

import com.jpexs.decompiler.flash.abc.avm2.model.BooleanAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.CoerceAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.ConvertAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.DecLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.FindPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.FullMultinameAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetPropertyAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.GetSlotAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.IncLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.IntegerValueAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.LocalRegAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NanAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NewActivationAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NewFunctionAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.NullAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetLocalAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetSlotAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.SetTypeAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.StoreNewActivationAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.UndefinedAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.clauses.DeclarationAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.clauses.ForEachInAVM2Item;
import com.jpexs.decompiler.flash.abc.avm2.model.clauses.ForInAVM2Item;
import com.jpexs.decompiler.graph.AbstractGraphTargetRecursiveVisitor;
import com.jpexs.decompiler.graph.AbstractGraphTargetVisitor;
import com.jpexs.decompiler.graph.Block;
import com.jpexs.decompiler.graph.GraphTargetItem;
import com.jpexs.decompiler.graph.TypeItem;
import com.jpexs.decompiler.graph.model.DoWhileItem;
import com.jpexs.decompiler.graph.model.FalseItem;
import com.jpexs.decompiler.graph.model.ForItem;
import com.jpexs.decompiler.graph.model.GotoItem;
import com.jpexs.decompiler.graph.model.IfItem;
import com.jpexs.decompiler.graph.model.LabelItem;
import com.jpexs.decompiler.graph.model.LoopItem;
import com.jpexs.decompiler.graph.model.SwitchItem;
import com.jpexs.decompiler.graph.model.WhileItem;
import com.jpexs.helpers.Reference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Moves declarations of local variables to the place where the variable is
 * first used.
 *
 * <p>
 * Compilers like Flash CS / Flex initialize every local variable with the
 * default value of its type at the beginning of the method (pushnull, coerce,
 * setlocal / setslot of the activation...). Without this, all locals are
 * declared at the top of the method with that default value and assigned again
 * later. In AS3, a var belongs to the whole function and starts with the
 * default value of its type anyway, so removing that initialization and
 * declaring the variable at its first assignment does not change the meaning of
 * the code.
 *
 * <p>
 * The declaration is placed in the innermost block that contains all uses of
 * the variable:
 * <ul>
 * <li>on the first statement of that block that uses the variable, when it is
 * an assignment to it (var a:int = ...), including for loop initializers and
 * for each / for in headers,</li>
 * <li>otherwise just before that statement, out of any loop, so the declaration
 * never looks like it resets the variable in each iteration.</li>
 * </ul>
 *
 * <p>
 * Variables are local registers and slots of the activation object. Slots are
 * left alone when the method has inner functions, which can use them too.
 *
 * @author JPEXS
 */
public class AVM2LocalDeclarationPlacement {

    /**
     * List of statements with its position in the tree.
     */
    private static class StatementList {

        public final List<GraphTargetItem> list;

        public final StatementList parent;

        public final int indexInParent;

        public final boolean insideLoop;

        public final boolean ownerIsLoop;

        /**
         * False for lists which are not a block of statements: for loop
         * initializer and update, while / do while condition.
         */
        public final boolean statementBlock;

        public final int depth;

        /**
         * Statement which holds this list, null for the method body.
         */
        public final GraphTargetItem owner;

        public StatementList(List<GraphTargetItem> list, StatementList parent, int indexInParent, GraphTargetItem owner, boolean ownerIsLoop, boolean statementBlock) {
            this.list = list;
            this.owner = owner;
            this.parent = parent;
            this.indexInParent = indexInParent;
            this.ownerIsLoop = ownerIsLoop;
            this.statementBlock = statementBlock;
            this.insideLoop = ownerIsLoop || (parent != null && parent.insideLoop);
            this.depth = parent == null ? 0 : parent.depth + 1;
        }
    }

    /**
     * Statement which uses a variable.
     */
    private static class Occurrence {

        public final StatementList statements;

        public final int index;

        public Occurrence(StatementList statements, int index) {
            this.statements = statements;
            this.index = index;
        }
    }

    /**
     * Position of a declaration in the tree.
     */
    private static class DeclarationPosition {

        public final StatementList statements;

        public final int index;

        /**
         * For each / for in statement whose header holds the declaration, or
         * null when the declaration is a statement.
         */
        public final GraphTargetItem header;

        public DeclarationPosition(StatementList statements, int index, GraphTargetItem header) {
            this.statements = statements;
            this.index = index;
            this.header = header;
        }
    }

    private final int firstLocalRegister;

    /**
     * Variables used by an item, without the blocks inside it. Register r has
     * key 2 * r, slot s has key 2 * s + 1.
     */
    private final Map<GraphTargetItem, BitSet> usedVariables = new IdentityHashMap<>();

    /**
     * Constructor.
     *
     * @param firstLocalRegister First register which is not this, a parameter,
     * arguments or rest
     */
    public AVM2LocalDeclarationPlacement(int firstLocalRegister) {
        this.firstLocalRegister = firstLocalRegister;
    }

    /**
     * Moves the declarations of local variables in method body items.
     *
     * @param items Method body items (with declarations already injected)
     */
    public void place(List<GraphTargetItem> items) {
        Reference<Boolean> unstructured = new Reference<>(false);
        Reference<Boolean> innerFunctions = new Reference<>(false);
        AbstractGraphTargetVisitor visitor = new AbstractGraphTargetVisitor() {
            @Override
            public boolean visit(GraphTargetItem item) {
                if ((item instanceof GotoItem) || (item instanceof LabelItem)) {
                    unstructured.setVal(true);
                }
                if (item instanceof NewFunctionAVM2Item) {
                    innerFunctions.setVal(true);
                }
                return !unstructured.getVal();
            }
        };
        for (GraphTargetItem item : items) {
            if (visitor.visit(item)) {
                item.visitRecursively(visitor);
            }
            if (unstructured.getVal()) {
                return;
            }
        }

        Set<Integer> prologueVariables = new LinkedHashSet<>();
        for (GraphTargetItem item : items) {
            if (item instanceof StoreNewActivationAVM2Item) {
                continue;
            }
            int variable = getPrologueVariable(item);
            if (variable == -1) {
                break;
            }
            prologueVariables.add(variable);
        }

        Set<Integer> declaredVariables = new LinkedHashSet<>();
        collectDeclaredVariables(items, declaredVariables);
        for (int variable : declaredVariables) {
            if (isSlot(variable) && innerFunctions.getVal()) {
                continue;
            }
            placeVariable(items, variable, prologueVariables.contains(variable));
        }
    }

    private static boolean isSlot(int variable) {
        return (variable & 1) == 1;
    }

    private void collectDeclaredVariables(List<GraphTargetItem> items, Set<Integer> declaredVariables) {
        for (GraphTargetItem item : items) {
            if (item instanceof DeclarationAVM2Item) {
                int variable = getDeclaredVariable((DeclarationAVM2Item) item);
                if (variable != -1) {
                    declaredVariables.add(variable);
                }
            }
            GraphTargetItem headerObject = getHeaderObject(item);
            if (headerObject instanceof DeclarationAVM2Item) {
                int variable = getDeclaredVariable((DeclarationAVM2Item) headerObject);
                if (variable != -1) {
                    declaredVariables.add(variable);
                }
            }
            if (item instanceof Block) {
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    collectDeclaredVariables(sub, declaredVariables);
                }
            }
        }
    }

    /**
     * Gets variable of the initialization the compiler puts at the beginning
     * of the method (var a:int = 0, var b:String = null...).
     *
     * @param item Item
     * @return Variable or -1 when it is not such initialization
     */
    private int getPrologueVariable(GraphTargetItem item) {
        if (!(item instanceof DeclarationAVM2Item)) {
            return -1;
        }
        DeclarationAVM2Item declaration = (DeclarationAVM2Item) item;
        if (!declaration.showValue) {
            return -1;
        }
        int variable = getDeclaredVariable(declaration);
        if (variable == -1 || declaration.assignment.value == null) {
            return -1;
        }
        if ((declaration.assignment instanceof SetSlotAVM2Item) && ((SetSlotAVM2Item) declaration.assignment).compoundOperator != null) {
            return -1;
        }
        if (!isDefaultValue(declaration.assignment.value, declaration.type)) {
            return -1;
        }
        return variable;
    }

    /**
     * Checks whether value is the value a variable of the type starts with.
     *
     * @param value Value
     * @param type Type
     * @return True if it is the default value
     */
    public static boolean isDefaultValue(GraphTargetItem value, GraphTargetItem type) {
        if (type == null || TypeItem.UNKNOWN.equals(type)) {
            return false;
        }
        while ((value instanceof CoerceAVM2Item) || (value instanceof ConvertAVM2Item)) {
            value = value.value;
        }
        if (TypeItem.INT.equals(type) || TypeItem.UINT.equals(type)) {
            return (value instanceof IntegerValueAVM2Item) && ((IntegerValueAVM2Item) value).value == 0;
        }
        if (TypeItem.NUMBER.equals(type)) {
            return (value instanceof NanAVM2Item) || isGlobalProperty(value, "NaN");
        }
        if (TypeItem.BOOLEAN.equals(type)) {
            return (value instanceof FalseItem) || ((value instanceof BooleanAVM2Item) && Boolean.FALSE.equals(((BooleanAVM2Item) value).value));
        }
        if (TypeItem.UNBOUNDED.equals(type)) {
            return (value instanceof UndefinedAVM2Item) || isGlobalProperty(value, "undefined");
        }
        if (!(type instanceof TypeItem)) {
            return false;
        }
        return value instanceof NullAVM2Item;
    }

    /**
     * Checks whether value reads a global property like undefined or NaN
     * (findpropstrict + getproperty).
     *
     * @param value Value
     * @param name Property name
     * @return True if it does
     */
    private static boolean isGlobalProperty(GraphTargetItem value, String name) {
        if (!(value instanceof GetPropertyAVM2Item)) {
            return false;
        }
        GetPropertyAVM2Item getProperty = (GetPropertyAVM2Item) value;
        if (!(getProperty.object instanceof FindPropertyAVM2Item) || !(getProperty.propertyName instanceof FullMultinameAVM2Item)) {
            return false;
        }
        return name.equals(((FullMultinameAVM2Item) getProperty.propertyName).resolvedMultinameName);
    }

    private static boolean isStatementBlock(GraphTargetItem owner, List<GraphTargetItem> sub) {
        if (owner instanceof ForItem) {
            return sub == ((ForItem) owner).commands;
        }
        if (owner instanceof WhileItem) {
            return sub == ((WhileItem) owner).commands;
        }
        if (owner instanceof DoWhileItem) {
            return sub == ((DoWhileItem) owner).commands;
        }
        return true;
    }

    private static boolean isLoop(GraphTargetItem item) {
        return (item instanceof LoopItem) && !(item instanceof SwitchItem);
    }

    private boolean usesVariable(GraphTargetItem item, int variable) {
        if (item == null) {
            return false;
        }
        BitSet variables = usedVariables.get(item);
        if (variables == null) {
            BitSet newVariables = new BitSet();
            int itemVariable = getUsedVariable(item);
            if (itemVariable >= 0) {
                newVariables.set(itemVariable);
            }
            item.visitRecursivelyNoBlock(new AbstractGraphTargetRecursiveVisitor() {
                @Override
                public void visit(GraphTargetItem subItem, Stack<GraphTargetItem> parentStack) {
                    int subItemVariable = getUsedVariable(subItem);
                    if (subItemVariable >= 0) {
                        newVariables.set(subItemVariable);
                    }
                }
            });
            variables = newVariables;
            usedVariables.put(item, variables);
        }
        return variables.get(variable);
    }

    /**
     * Gets variable used by an item. Any slot access counts, not only the
     * activation ones, so a use is never missed.
     *
     * @param item Item
     * @return Variable or -1
     */
    private static int getUsedVariable(GraphTargetItem item) {
        int reg = -1;
        if (item instanceof LocalRegAVM2Item) {
            reg = ((LocalRegAVM2Item) item).regIndex;
        } else if (item instanceof SetLocalAVM2Item) {
            reg = ((SetLocalAVM2Item) item).regIndex;
        } else if (item instanceof IncLocalAVM2Item) {
            reg = ((IncLocalAVM2Item) item).regIndex;
        } else if (item instanceof DecLocalAVM2Item) {
            reg = ((DecLocalAVM2Item) item).regIndex;
        } else if (item instanceof GetSlotAVM2Item) {
            return slotVariable(((GetSlotAVM2Item) item).slotIndex);
        } else if (item instanceof SetSlotAVM2Item) {
            return slotVariable(((SetSlotAVM2Item) item).slotIndex);
        }
        if (reg < 0) {
            return -1;
        }
        return 2 * reg;
    }

    private static int slotVariable(int slotIndex) {
        if (slotIndex < 0) {
            return -1;
        }
        return 2 * slotIndex + 1;
    }

    /**
     * Gets variable of an assignment to a local register (not a parameter) or
     * to a slot of the activation object.
     *
     * @param item Item
     * @return Variable or -1
     */
    private int getAssignedVariable(GraphTargetItem item) {
        if (item instanceof SetLocalAVM2Item) {
            int reg = ((SetLocalAVM2Item) item).regIndex;
            return reg >= firstLocalRegister ? 2 * reg : -1;
        }
        if (item instanceof LocalRegAVM2Item) {
            int reg = ((LocalRegAVM2Item) item).regIndex;
            return reg >= firstLocalRegister ? 2 * reg : -1;
        }
        if (item instanceof SetSlotAVM2Item) {
            SetSlotAVM2Item setSlot = (SetSlotAVM2Item) item;
            if ((setSlot.scope instanceof NewActivationAVM2Item) && setSlot.slotName != null) {
                return slotVariable(setSlot.slotIndex);
            }
        }
        if (item instanceof GetSlotAVM2Item) {
            GetSlotAVM2Item getSlot = (GetSlotAVM2Item) item;
            if ((getSlot.scope instanceof NewActivationAVM2Item) && getSlot.slotName != null) {
                return slotVariable(getSlot.slotIndex);
            }
        }
        return -1;
    }

    private int getDeclaredVariable(DeclarationAVM2Item declaration) {
        return getAssignedVariable(declaration.assignment);
    }

    private static GraphTargetItem getHeaderObject(GraphTargetItem item) {
        if (item instanceof ForEachInAVM2Item) {
            return ((ForEachInAVM2Item) item).expression.object;
        }
        if (item instanceof ForInAVM2Item) {
            return ((ForInAVM2Item) item).expression.object;
        }
        return null;
    }

    private static GraphTargetItem getHeaderCollection(GraphTargetItem item) {
        if (item instanceof ForEachInAVM2Item) {
            return ((ForEachInAVM2Item) item).expression.collection;
        }
        return ((ForInAVM2Item) item).expression.collection;
    }

    private static void setHeaderObject(GraphTargetItem item, GraphTargetItem object) {
        if (item instanceof ForEachInAVM2Item) {
            ((ForEachInAVM2Item) item).expression.object = object;
        }
        if (item instanceof ForInAVM2Item) {
            ((ForInAVM2Item) item).expression.object = object;
        }
    }

    private void walk(StatementList statements, int variable, List<Occurrence> occurrences, List<DeclarationPosition> declarations) {
        List<GraphTargetItem> list = statements.list;
        for (int i = 0; i < list.size(); i++) {
            GraphTargetItem item = list.get(i);
            if ((item instanceof DeclarationAVM2Item) && getDeclaredVariable((DeclarationAVM2Item) item) == variable) {
                declarations.add(new DeclarationPosition(statements, i, null));
            }
            GraphTargetItem headerObject = getHeaderObject(item);
            if ((headerObject instanceof DeclarationAVM2Item) && getDeclaredVariable((DeclarationAVM2Item) headerObject) == variable) {
                declarations.add(new DeclarationPosition(statements, i, item));
            }
            if (usesVariable(item, variable)) {
                occurrences.add(new Occurrence(statements, i));
            }
            if (item instanceof Block) {
                boolean loop = isLoop(item);
                for (List<GraphTargetItem> sub : ((Block) item).getSubs()) {
                    walk(new StatementList(sub, statements, i, item, loop, isStatementBlock(item, sub)), variable, occurrences, declarations);
                }
            }
        }
    }

    private void placeVariable(List<GraphTargetItem> items, int variable, boolean prologue) {
        StatementList root = new StatementList(items, null, -1, null, false, true);
        List<DeclarationPosition> declarations = new ArrayList<>();
        walk(root, variable, new ArrayList<>(), declarations);
        if (declarations.size() != 1) {
            return;
        }
        DeclarationPosition position = declarations.get(0);
        GraphTargetItem declarationItem = position.header != null ? getHeaderObject(position.header) : position.statements.list.get(position.index);
        DeclarationAVM2Item declaration = (DeclarationAVM2Item) declarationItem;

        //Take the declaration away, then find where it belongs
        GraphTargetItem prologueAssignment = null;
        if (prologue && position.statements == root && position.header == null) {
            prologueAssignment = declaration.assignment;
            items.remove(position.index);
        } else {
            undeclare(position, declaration);
        }

        List<Occurrence> occurrences = new ArrayList<>();
        root = new StatementList(items, null, -1, null, false, true);
        walk(root, variable, occurrences, new ArrayList<>());
        if (occurrences.isEmpty()) {
            //Not used anymore, keep it as it was
            if (prologueAssignment != null) {
                items.add(position.index, declaration);
            } else {
                redeclare(position, declaration);
            }
            return;
        }

        //Innermost list containing all occurrences
        StatementList target = occurrences.get(0).statements;
        for (Occurrence occurrence : occurrences) {
            target = getCommonList(target, occurrence.statements);
        }
        int targetIndex = Integer.MAX_VALUE;
        for (Occurrence occurrence : occurrences) {
            StatementList statements = occurrence.statements;
            int index = occurrence.index;
            while (statements != target) {
                index = statements.indexInParent;
                statements = statements.parent;
            }
            targetIndex = Math.min(targetIndex, index);
        }

        while (!target.statementBlock) {
            targetIndex = target.indexInParent;
            target = target.parent;
        }

        GraphTargetItem first = target.list.get(targetIndex);

        //var a:T = value;
        if (isDirectAssignment(first, variable)) {
            declareAssignment(target.list, targetIndex, declaration);
            return;
        }

        //for (var a:T = value; ...)
        if (first instanceof ForItem) {
            ForItem forItem = (ForItem) first;
            if (forItem.firstCommands != null) {
                for (int i = 0; i < forItem.firstCommands.size(); i++) {
                    GraphTargetItem command = forItem.firstCommands.get(i);
                    if (isDirectAssignment(command, variable)) {
                        declareAssignment(forItem.firstCommands, i, declaration);
                        return;
                    }
                    if (usesVariable(command, variable)) {
                        break;
                    }
                }
            }
        }

        //for each (var a:T in ...)
        GraphTargetItem headerObject = getHeaderObject(first);
        if (headerObject != null && getAssignedVariable(headerObject) == variable
                && !usesVariable(getHeaderCollection(first), variable)) {
            declaration.assignment = headerObject;
            declaration.showValue = true;
            setHeaderObject(first, declaration);
            return;
        }

        //var a:T; just before the first use, out of loops
        while (target.insideLoop) {
            StatementList outer = target;
            while (!outer.ownerIsLoop) {
                outer = outer.parent;
            }
            targetIndex = outer.indexInParent;
            target = outer.parent;
        }
        //and out of else if chains, which would become else { var a:T; if ... }
        while (isElseIf(target)) {
            targetIndex = target.indexInParent;
            target = target.parent;
        }
        if (prologueAssignment != null) {
            declaration.assignment = prologueAssignment;
            declaration.showValue = true;
        } else {
            declaration.assignment = createDeclarationAssignment(declaration);
            declaration.showValue = false;
        }
        target.list.add(targetIndex, declaration);
    }

    /**
     * Creates the assignment of a declaration without value.
     *
     * @param declaration Declaration
     * @return Assignment which names the variable
     */
    private static GraphTargetItem createDeclarationAssignment(DeclarationAVM2Item declaration) {
        GraphTargetItem assignment = declaration.assignment;
        if (assignment instanceof SetLocalAVM2Item) {
            return new SetLocalAVM2Item(null, null, ((SetLocalAVM2Item) assignment).regIndex, new UndefinedAVM2Item(null, null), declaration.type);
        }
        if (assignment instanceof LocalRegAVM2Item) {
            return new SetLocalAVM2Item(null, null, ((LocalRegAVM2Item) assignment).regIndex, new UndefinedAVM2Item(null, null), declaration.type);
        }
        if (assignment instanceof SetSlotAVM2Item) {
            SetSlotAVM2Item setSlot = (SetSlotAVM2Item) assignment;
            return new SetSlotAVM2Item(null, null, setSlot.scope, setSlot.slotObject, setSlot.slotIndex, setSlot.slotName, new UndefinedAVM2Item(null, null), declaration.type);
        }
        GetSlotAVM2Item getSlot = (GetSlotAVM2Item) assignment;
        return new SetSlotAVM2Item(null, null, getSlot.scope, getSlot.slotObject, getSlot.slotIndex, getSlot.slotName, new UndefinedAVM2Item(null, null), declaration.type);
    }

    private static boolean isElseIf(StatementList statements) {
        if (!(statements.owner instanceof IfItem)) {
            return false;
        }
        IfItem ifItem = (IfItem) statements.owner;
        return statements.list == ifItem.onFalse && ifItem.onFalse.size() == 1 && (ifItem.onFalse.get(0) instanceof IfItem);
    }

    private static StatementList getCommonList(StatementList a, StatementList b) {
        while (a.depth > b.depth) {
            a = a.parent;
        }
        while (b.depth > a.depth) {
            b = b.parent;
        }
        while (a != b) {
            a = a.parent;
            b = b.parent;
        }
        return a;
    }

    private boolean isDirectAssignment(GraphTargetItem item, int variable) {
        if (!(item instanceof SetLocalAVM2Item) && !(item instanceof SetSlotAVM2Item)) {
            return false;
        }
        if ((item instanceof SetSlotAVM2Item) && ((SetSlotAVM2Item) item).compoundOperator != null) {
            return false;
        }
        return getAssignedVariable(item) == variable && item.value != null && !usesVariable(item.value, variable);
    }

    private static void declareAssignment(List<GraphTargetItem> list, int index, DeclarationAVM2Item declaration) {
        GraphTargetItem assignment = list.get(index);
        declaration.assignment = assignment;
        declaration.showValue = true;
        ((SetTypeAVM2Item) assignment).setDeclaration(declaration);
        list.set(index, declaration);
    }

    private void undeclare(DeclarationPosition position, DeclarationAVM2Item declaration) {
        if (position.header != null) {
            setHeaderObject(position.header, declaration.assignment);
            return;
        }
        if (declaration.showValue) {
            position.statements.list.set(position.index, declaration.assignment);
        } else {
            position.statements.list.remove(position.index);
        }
    }

    private void redeclare(DeclarationPosition position, DeclarationAVM2Item declaration) {
        if (position.header != null) {
            setHeaderObject(position.header, declaration);
            return;
        }
        if (declaration.showValue) {
            position.statements.list.set(position.index, declaration);
        } else {
            position.statements.list.add(position.index, declaration);
        }
    }
}
