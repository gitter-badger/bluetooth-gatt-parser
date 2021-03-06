package org.sputnikdev.bluetooth.gattparser.spec;

/*-
 * #%L
 * org.sputnikdev:bluetooth-gatt-parser
 * %%
 * Copyright (C) 2017 Sputnik Dev
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.sputnikdev.bluetooth.gattparser.MockUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FlagUtilsTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Field flagField;

    @Test
    public void testGetReadFlags() throws Exception {
        List<Bit> bits = new ArrayList<>();
        when(flagField.getBitField().getBits()).thenReturn(bits);
        when(flagField.getFormat().getSize()).thenReturn(15);
        bits.add(MockUtils.mockBit(0, 1, "A"));
        bits.add(MockUtils.mockBit(1, 2, "B"));
        bits.add(MockUtils.mockBit(2, 1, "C"));
        bits.add(MockUtils.mockBit(3, 3, "D"));
        bits.add(MockUtils.mockBit(4, 2, "E"));
        bits.add(MockUtils.mockBit(5, 2, "F"));
        bits.add(MockUtils.mockBit(6, 4, "G"));

        byte[] raw = new byte[] { (byte) 0b10100101, (byte) 0b01010001 };
        int[] flagsValues = FlagUtils.parseReadFlags(flagField, raw);
        assertArrayEquals(new int[] {1, 2, 0, 2, 3, 0, 10}, flagsValues);

        Set<String> flags = FlagUtils.getReadFlags(flagField, raw);
        assertEquals(7, flags.size());
        assertTrue(flags.containsAll(Arrays.asList("A1", "B2", "C0", "D2", "E3", "F0", "G10")));
    }

    @Test
    public void testGetWriteFlag() throws Exception {
        List<Enumeration> enumerations = new ArrayList<>();
        enumerations.add(MockUtils.mockEnumeration(1, "C1"));
        enumerations.add(MockUtils.mockEnumeration(2, null));
        enumerations.add(MockUtils.mockEnumeration(3, "C2"));
        when(flagField.getEnumerations().getEnumerations()).thenReturn(enumerations);

        assertEquals("C1", FlagUtils.getWriteFlag(flagField, 1));
        assertNull(FlagUtils.getWriteFlag(flagField, 2));
        assertEquals("C2", FlagUtils.getWriteFlag(flagField, 3));

        assertNull(FlagUtils.getWriteFlag(flagField, null));
        when(flagField.getEnumerations().getEnumerations()).thenReturn(null);
        assertNull(FlagUtils.getWriteFlag(flagField, 1));

    }

    @Test
    public void testGetAllWriteFlags() {
        assertTrue(FlagUtils.getAllWriteFlags(flagField).isEmpty());

        List<Enumeration> enumerations = new ArrayList<>();
        enumerations.add(MockUtils.mockEnumeration(1, "C1"));
        enumerations.add(MockUtils.mockEnumeration(2, null));
        enumerations.add(MockUtils.mockEnumeration(3, "C2"));
        when(flagField.getEnumerations().getEnumerations()).thenReturn(enumerations);
        assertTrue(FlagUtils.getAllWriteFlags(flagField).containsAll(Arrays.asList("C1", "C2")));
    }

}
