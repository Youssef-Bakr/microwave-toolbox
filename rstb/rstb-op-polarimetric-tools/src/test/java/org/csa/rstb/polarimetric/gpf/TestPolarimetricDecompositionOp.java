/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.csa.rstb.polarimetric.gpf;

import eu.esa.sar.commons.test.TestData;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.engine_utilities.util.TestUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Unit test for PolarimetricDecompositionOp.
 */
public class TestPolarimetricDecompositionOp {

    static {
        TestUtils.initTestEnvironment();
    }

    private final static OperatorSpi spi = new PolarimetricDecompositionOp.Spi();

    private final static String inputPathQuad = TestData.inputSAR + "/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900.dim";
    private final static String inputQuadFullStack = TestData.inputSAR + "/QuadPolStack/RS2-Quad_Pol_Stack.dim";
    private final static String inputC3Stack = TestData.inputSAR + "/QuadPolStack/RS2-C3-Stack.dim";
    private final static String inputT3Stack = TestData.inputSAR + "/QuadPolStack/RS2-T3-Stack.dim";

    private final static String expectedSinclair = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_Sinclair.dim";
    private final static String expectedPauli = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_Pauli.dim";
    private final static String expectedFreeman = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_FreemanDurden.dim";
    private final static String expectedYamaguchi = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_Yamaguchi.dim";
    private final static String expectedVanZyl = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_VanZyl.dim";
    private final static String expectedCloude = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_Cloude.dim";
    private final static String expectedHaAlpha = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_HaAlpha.dim";
    private final static String expectedTouzi = TestData.input + "/expected/QuadPol/QuadPol_subset_0_of_RS2-SLC-PDS_00058900_Touzi.dim";


    @Before
    public void setUp() {
        // If any of the file does not exist: the test will be ignored
        assumeTrue(inputPathQuad + " not found", new File(inputPathQuad).exists());
        assumeTrue(inputQuadFullStack + " not found", new File(inputQuadFullStack).exists());
        assumeTrue(inputC3Stack + " not found", new File(inputC3Stack).exists());
        assumeTrue(inputT3Stack + " not found", new File(inputT3Stack).exists());

        assumeTrue(expectedSinclair + " not found", new File(expectedSinclair).exists());
        assumeTrue(expectedPauli + " not found", new File(expectedPauli).exists());
        assumeTrue(expectedFreeman + " not found", new File(expectedFreeman).exists());
        assumeTrue(expectedYamaguchi + " not found", new File(expectedYamaguchi).exists());
        assumeTrue(expectedVanZyl + " not found", new File(expectedVanZyl).exists());
        assumeTrue(expectedCloude + " not found", new File(expectedCloude).exists());
        assumeTrue(expectedHaAlpha + " not found", new File(expectedHaAlpha).exists());
        assumeTrue(expectedTouzi + " not found", new File(expectedTouzi).exists());
    }

    private Product runDecomposition(final PolarimetricDecompositionOp op,
                                     final String decompositionName, final String path) throws Exception {
        final File inputFile = new File(path);
        final Product sourceProduct = TestUtils.readSourceProduct(inputFile);

        assertNotNull(op);
        op.setSourceProduct(sourceProduct);
        op.SetDecomposition(decompositionName);

        // get targetProduct: execute initialize()
        final Product targetProduct = op.getTargetProduct();
        TestUtils.verifyProduct(targetProduct, false, false);
        return targetProduct;
    }

    /**
     * Perform Sinclair decomposition of a Radarsat-2 product and compares it with processed product known to be correct
     *
     * @throws Exception general exception
     */
    @Test
    public void testSinclairDecomposition() throws Exception {
        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.SINCLAIR_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedSinclair, null);
    }

    /**
     * Perform Pauli decomposition of a Radarsat-2 product and compares it with processed product known to be correct
     *
     * @throws Exception general exception
     */
    @Test
    public void testPauliDecomposition() throws Exception {

        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.PAULI_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedPauli, null);
    }

    /**
     * Perform Freeman-Durden decomposition of a Radarsat-2 product and compares it with processed product known to be correct
     *
     * @throws Exception general exception
     */
    @Test
    public void testFreemanDecomposition() throws Exception {

        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.FREEMAN_DURDEN_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedFreeman, null);
    }

    @Test
    public void testCloudeDecomposition() throws Exception {

        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.CLOUDE_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedCloude, null);
    }

    /**
     * Perform H-A-Alpha decomposition of a Radarsat-2 product and compares it with processed product known to be correct
     *
     * @throws Exception general exception
     */
    @Test
    public void testHAAlphaDecomposition() throws Exception {

        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setHAAlphaParameters(true, true, true, true);
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.H_A_ALPHA_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedHaAlpha, null);
    }

    public void testTouziDecomposition() throws Exception {

        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setTouziParameters(true, true, true, true);
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.TOUZI_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedTouzi, null);
    }

    @Test
    public void testVanZylDecomposition() throws Exception {

        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.VANZYL_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedVanZyl, null);
    }

    @Test
    public void testYamaguchiDecomposition() throws Exception {

        final PolarimetricDecompositionOp op = (PolarimetricDecompositionOp) spi.createOperator();
        final Product targetProduct = runDecomposition(op,
                PolarimetricDecompositionOp.YAMAGUCHI_DECOMPOSITION, inputPathQuad);
        if (targetProduct != null)
            TestUtils.compareProducts(targetProduct, expectedYamaguchi, null);
    }

    // Quad Pol Stack

    @Test
    public void testSinclairStack() throws Exception {

        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.SINCLAIR_DECOMPOSITION, inputQuadFullStack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.SINCLAIR_DECOMPOSITION, inputC3Stack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.SINCLAIR_DECOMPOSITION, inputT3Stack);
    }

    @Test
    public void testPauliStack() throws Exception {

        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.PAULI_DECOMPOSITION, inputQuadFullStack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.PAULI_DECOMPOSITION, inputC3Stack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.PAULI_DECOMPOSITION, inputT3Stack);
    }

    @Test
    public void testFreemanStack() throws Exception {

        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.FREEMAN_DURDEN_DECOMPOSITION, inputQuadFullStack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.FREEMAN_DURDEN_DECOMPOSITION, inputC3Stack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.FREEMAN_DURDEN_DECOMPOSITION, inputT3Stack);
    }

    @Test
    public void testCloudeStack() throws Exception {

        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.CLOUDE_DECOMPOSITION, inputQuadFullStack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.CLOUDE_DECOMPOSITION, inputC3Stack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.CLOUDE_DECOMPOSITION, inputT3Stack);
    }

    @Test
    public void testHAAlphaStack() throws Exception {

        PolarimetricDecompositionOp op;

        op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setHAAlphaParameters(true, true, true, true);
        runDecomposition(op, PolarimetricDecompositionOp.H_A_ALPHA_DECOMPOSITION, inputQuadFullStack);

        op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setHAAlphaParameters(true, true, true, true);
        runDecomposition(op, PolarimetricDecompositionOp.H_A_ALPHA_DECOMPOSITION, inputC3Stack);

        op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setHAAlphaParameters(true, true, true, true);
        runDecomposition(op, PolarimetricDecompositionOp.H_A_ALPHA_DECOMPOSITION, inputT3Stack);
    }

    @Test
    public void testTouziStack() throws Exception {

        PolarimetricDecompositionOp op;

        op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setTouziParameters(true, true, true, true);
        runDecomposition(op, PolarimetricDecompositionOp.TOUZI_DECOMPOSITION, inputQuadFullStack);

        op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setTouziParameters(true, true, true, true);
        runDecomposition(op, PolarimetricDecompositionOp.TOUZI_DECOMPOSITION, inputC3Stack);

        op = (PolarimetricDecompositionOp) spi.createOperator();
        op.setTouziParameters(true, true, true, true);
        runDecomposition(op, PolarimetricDecompositionOp.TOUZI_DECOMPOSITION, inputT3Stack);
    }

    @Test
    public void testVanZylStack() throws Exception {

        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.VANZYL_DECOMPOSITION, inputQuadFullStack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.VANZYL_DECOMPOSITION, inputC3Stack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.VANZYL_DECOMPOSITION, inputT3Stack);
    }

    public void testYamaguchiStack() throws Exception {

        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.YAMAGUCHI_DECOMPOSITION, inputQuadFullStack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.YAMAGUCHI_DECOMPOSITION, inputC3Stack);
        runDecomposition((PolarimetricDecompositionOp) spi.createOperator(),
                PolarimetricDecompositionOp.YAMAGUCHI_DECOMPOSITION, inputT3Stack);
    }
}
