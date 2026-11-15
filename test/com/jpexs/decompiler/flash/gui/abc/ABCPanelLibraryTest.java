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
package com.jpexs.decompiler.flash.gui.abc;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.configuration.Configuration;
import com.jpexs.decompiler.flash.configuration.CustomConfigurationKeys;
import com.jpexs.decompiler.flash.configuration.SwfSpecificCustomConfiguration;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Showing an ABC selects the stored library of its SWF in the library
 * combobox. That must not be taken as a change of the library, which clears
 * the script cache of the SWF (the script packs of the tree then do not match
 * the packs of search results anymore).
 *
 * @author JPEXS
 */
public class ABCPanelLibraryTest {

    @Test
    public void testStoredLibraryIsNotAChange() {
        SWF swf = new SWF("UTF-8");
        String title = "ABCPanelLibraryTest_" + System.nanoTime() + ".swf";
        swf.setFileTitle(title);
        try {
            Assert.assertFalse(ABCPanel.isLibraryChanged(swf, SWF.LIBRARY_FLASH), "no stored library means flash");
            Assert.assertTrue(ABCPanel.isLibraryChanged(swf, SWF.LIBRARY_AIR));

            SwfSpecificCustomConfiguration conf = Configuration.getOrCreateSwfSpecificCustomConfiguration(title);
            conf.setCustomData(CustomConfigurationKeys.KEY_LIBRARY, "" + SWF.LIBRARY_AIR);
            Assert.assertFalse(ABCPanel.isLibraryChanged(swf, SWF.LIBRARY_AIR), "stored air library selected again");
            Assert.assertTrue(ABCPanel.isLibraryChanged(swf, SWF.LIBRARY_FLASH));
        } finally {
            Configuration.swfSpecificCustomConfigs.get().remove(title);
        }
    }
}
