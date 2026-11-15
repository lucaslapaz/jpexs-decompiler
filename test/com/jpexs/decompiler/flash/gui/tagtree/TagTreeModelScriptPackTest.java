/*
 *  Copyright (C) 2010-2026 JPEXS
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpexs.decompiler.flash.gui.tagtree;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.abc.ScriptPack;
import com.jpexs.decompiler.flash.gui.AppStrings;
import com.jpexs.decompiler.flash.gui.MainFrame;
import com.jpexs.decompiler.flash.treeitems.OpenableList;
import com.jpexs.decompiler.flash.treeitems.TreeItem;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.tree.TreePath;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Script packs are recreated when the script cache of the SWF is cleared
 * (library or linked SWFs change, trait removal...). The tree still holds the
 * old ones, and the new ones (from SWF.getAS3Packs, like search results) must
 * still be found in it.
 *
 * @author JPEXS
 */
public class TagTreeModelScriptPackTest {

    private static final String SWF_FILE = "libsrc/ffdec_lib/testdata/as3_new/bin/as3_new.flex.swf";

    private static ScriptPack findPack(SWF swf, String classPath) {
        for (ScriptPack pack : swf.getAS3Packs()) {
            if (pack.getClassPath().toRawString().equals(classPath)) {
                return pack;
            }
        }
        return null;
    }

    @BeforeClass
    public void init() {
        AppStrings.setResourceClass(MainFrame.class);
    }

    @Test
    public void testScriptPackFoundAfterScriptCacheClear() throws IOException, InterruptedException {
        SWF swf;
        try (InputStream is = new BufferedInputStream(new FileInputStream(SWF_FILE))) {
            swf = new SWF(is, false);
        }
        OpenableList list = new OpenableList();
        list.items.add(swf);
        swf.setOpenableList(list);
        TagTreeModel model = new TagTreeModel(Arrays.asList(list), false);

        String classPath = "tests_classes.mypackage1.TestClass2";
        ScriptPack oldPack = findPack(swf, classPath);
        Assert.assertNotNull(oldPack);
        Assert.assertNotNull(model.getTreePath(oldPack), "pack in the tree");

        swf.clearScriptCache();
        ScriptPack newPack = findPack(swf, classPath);
        Assert.assertNotNull(newPack);
        Assert.assertNotSame(newPack, oldPack, "packs recreated");

        TreePath path = model.getTreePath(newPack);
        Assert.assertNotNull(path, "new pack found in the tree");
        Assert.assertEquals(path.getLastPathComponent(), newPack);

        List<TreeItem> items = new ArrayList<>();
        items.add(newPack);
        Map<TreeItem, TreePath> paths = model.getTreePathMulti(items);
        Assert.assertNotNull(paths.get(newPack), "new pack found by getTreePathMulti");
        Map<TreeItem, TreeItem> parents = model.getTreePathParentMulti(items);
        Assert.assertNotNull(parents.get(newPack), "new pack found by getTreePathParentMulti");
    }
}
