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

import com.bc.ceres.core.ProgressMonitor;
import org.csa.rstb.polarimetric.gpf.support.DualPolProcessor;
import org.csa.rstb.polarimetric.gpf.support.QuadPolProcessor;
import eu.esa.sar.commons.polsar.PolBandUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.gpf.InputProductValidator;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Generate polarimetric covariance or coherency matrix for a given dual or full pol product
 */

@OperatorMetadata(alias = "Polarimetric-Matrices",
        category = "Radar/Polarimetric",
        authors = "Jun Lu, Luis Veci",
        version = "1.0",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Generates covariance or coherency matrix for given product")
public final class PolarimetricMatricesOp extends Operator implements DualPolProcessor, QuadPolProcessor {

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(valueSet = {C2, C3, C4, T3, T4}, description = "The covariance or coherency matrix",
            defaultValue = T3, label = "Polarimetric Matrix")
    private String matrix = T3;

    private PolBandUtils.PolSourceBand[] srcBandList;
    private final Map<Band, MatrixElem> matrixBandMap = new HashMap<>(8);

    private PolBandUtils.MATRIX matrixType = PolBandUtils.MATRIX.C3;
    private PolBandUtils.MATRIX sourceProductType = null;

    public static final String C2 = "C2";
    public static final String C3 = "C3"; // set to public because unit tests need to use it
    public static final String C4 = "C4";
    public static final String T3 = "T3";
    public static final String T4 = "T4";

    /**
     * Set matrix type. This function is used by unit test only.
     *
     * @param s The matrix type.
     */
    public void SetMatrixType(final String s) {

        if (s.equals(C2) || s.equals(C3) || s.equals(C4) || s.equals(T3) || s.equals(T4)) {
            matrix = s;
        } else {
            throw new OperatorException(s + " is an invalid filter name.");
        }
    }

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link Product} annotated with the
     * {@link TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws OperatorException If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {
        try {
            final InputProductValidator validator = new InputProductValidator(sourceProduct);
            validator.checkIfSARProduct();
            validator.checkIfTOPSARBurstProduct(false);
            validator.checkIfSLC();

            sourceProductType = PolBandUtils.getSourceProductType(sourceProduct);

            checkSourceProductType();

            srcBandList = PolBandUtils.getSourceBands(sourceProduct, sourceProductType);

            createTargetProduct();

            updateTargetProductMetadata();
        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());

        addSelectedBands();

        ProductUtils.copyProductNodes(sourceProduct, targetProduct);
    }

    /**
     * Add bands to the target product.
     *
     * @throws OperatorException The exception.
     */
    private void addSelectedBands() throws OperatorException {

        String[] bandNames;
        switch (matrix) {
            case C2:

                bandNames = PolBandUtils.getC2BandNames();
                matrixType = PolBandUtils.MATRIX.C2;

                break;
            case C3:

                bandNames = PolBandUtils.getC3BandNames();
                matrixType = PolBandUtils.MATRIX.C3;

                break;
            case C4:

                bandNames = PolBandUtils.getC4BandNames();
                matrixType = PolBandUtils.MATRIX.C4;

                break;
            case T3:

                bandNames = PolBandUtils.getT3BandNames();
                matrixType = PolBandUtils.MATRIX.T3;

                break;
            case T4:

                bandNames = PolBandUtils.getT4BandNames();
                matrixType = PolBandUtils.MATRIX.T4;

                break;
            default:
                throw new OperatorException("Unknown matrix type: " + matrix);
        }

        for (PolBandUtils.PolSourceBand bandList : srcBandList) {
            final Band[] targetBands = OperatorUtils.addBands(targetProduct, bandNames, bandList.suffix);
            bandList.addTargetBands(targetBands);
        }

        mapMatrixElemToBands();
    }

    private void checkSourceProductType() {

        if (sourceProductType == PolBandUtils.MATRIX.UNKNOWN) {
            // This check will catch products with a single pol.
            throw new OperatorException("Input should be a polarimetric product");
        }

        if (matrix.equals(C2)) {
            if (sourceProductType != PolBandUtils.MATRIX.DUAL_HH_HV &&
                    sourceProductType != PolBandUtils.MATRIX.DUAL_VH_VV &&
                    sourceProductType != PolBandUtils.MATRIX.DUAL_HH_VV &&
                    sourceProductType != PolBandUtils.MATRIX.LCHCP &&
                    sourceProductType != PolBandUtils.MATRIX.RCHCP) {
                if (sourceProductType == PolBandUtils.MATRIX.FULL) {
                    throw new OperatorException("Dual-pol product is expected for C2. Use BandSelect to select polarizations");
                } else {
                    throw new OperatorException("Dual-pol polarimetric product is expected for C2");
                }
            }
        } else {
            if (sourceProductType == PolBandUtils.MATRIX.T4) {
                if (matrix.equals(T4)) {
                    throw new OperatorException("The source product is already in T4 format, no conversion is needed");
                }
            } else if (sourceProductType == PolBandUtils.MATRIX.C4) {
                if (matrix.equals(C4)) {
                    throw new OperatorException("The source product is already in C4 format, no conversion is needed");
                }
            } else if (sourceProductType == PolBandUtils.MATRIX.T3) {
                switch (matrix) {
                    case T4:
                        throw new OperatorException("Cannot convert source product from T3 format to T4 format");
                    case C4:
                        throw new OperatorException("Cannot convert source product from T3 format to C4 format");
                    case T3:
                        throw new OperatorException("The source product is already in T3 format, no conversion is needed");
                }
            } else if (sourceProductType == PolBandUtils.MATRIX.C3) {
                switch (matrix) {
                    case T4:
                        throw new OperatorException("Cannot convert source product from C3 format to T4 format");
                    case C4:
                        throw new OperatorException("Cannot convert source product from C3 format to C4 format");
                    case C3:
                        throw new OperatorException("The source product is already in C3 format, no conversion is needed");
                }
            } else if (sourceProductType != PolBandUtils.MATRIX.FULL) {
                throw new OperatorException("Full-pol polarimetric product is expected");
            }
        }
    }

    private void mapMatrixElemToBands() {
        final Band[] bands = targetProduct.getBands();
        for (Band band : bands) {
            final String targetBandName = band.getName();

            if (PolBandUtils.isBandForMatrixElement(targetBandName, "11")) {
                matrixBandMap.put(band, new MatrixElem(0, 0, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "12_real")) {
                matrixBandMap.put(band, new MatrixElem(0, 1, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "12_imag")) {
                matrixBandMap.put(band, new MatrixElem(0, 1, true));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "13_real")) {
                matrixBandMap.put(band, new MatrixElem(0, 2, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "13_imag")) {
                matrixBandMap.put(band, new MatrixElem(0, 2, true));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "14_real")) {
                matrixBandMap.put(band, new MatrixElem(0, 3, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "14_imag")) {
                matrixBandMap.put(band, new MatrixElem(0, 3, true));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "22")) {
                matrixBandMap.put(band, new MatrixElem(1, 1, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "23_real")) {
                matrixBandMap.put(band, new MatrixElem(1, 2, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "23_imag")) {
                matrixBandMap.put(band, new MatrixElem(1, 2, true));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "24_real")) {
                matrixBandMap.put(band, new MatrixElem(1, 3, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "24_imag")) {
                matrixBandMap.put(band, new MatrixElem(1, 3, true));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "33")) {
                matrixBandMap.put(band, new MatrixElem(2, 2, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "34_real")) {
                matrixBandMap.put(band, new MatrixElem(2, 3, false));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "34_imag")) {
                matrixBandMap.put(band, new MatrixElem(2, 3, true));
            } else if (PolBandUtils.isBandForMatrixElement(targetBandName, "44")) {
                matrixBandMap.put(band, new MatrixElem(3, 3, false));
            }
        }
    }

    /**
     * Update metadata in the target product.
     */
    private void updateTargetProductMetadata() throws Exception {
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(targetProduct);

        absRoot.setAttributeInt(AbstractMetadata.polsarData, 1);

        // Save new slave band names
        PolBandUtils.saveNewBandNames(targetProduct, srcBandList);
    }

    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w = targetRectangle.width;
        final int h = targetRectangle.height;
        final int maxY = y0 + h;
        final int maxX = x0 + w;

        final double[] kr = new double[2];
        final double[] ki = new double[2];

        int matrixDim;
        if (matrixType.equals(PolBandUtils.MATRIX.C2)) {
            matrixDim = 2;
        } else if (matrixType.equals(PolBandUtils.MATRIX.C3) || matrixType.equals(PolBandUtils.MATRIX.T3)) {
            matrixDim = 3;
        } else { // matrixType.equals(MATRIX.C4) || matrixType.equals(MATRIX.T4)
            matrixDim = 4;
        }

        final double[][] tempRe = new double[matrixDim][matrixDim];
        final double[][] tempIm = new double[matrixDim][matrixDim];

        for (final PolBandUtils.PolSourceBand bandList : srcBandList) {
            try {
                // save tile data for quicker access
                final TileData[] tileDataList = new TileData[bandList.targetBands.length];
                int i = 0;
                for (Band targetBand : bandList.targetBands) {
                    final Tile targetTile = targetTiles.get(targetBand);
                    final MatrixElem elem = matrixBandMap.get(targetBand);

                    tileDataList[i++] = new TileData(targetTile, elem);
                }

                final Tile[] sourceTiles = new Tile[bandList.srcBands.length];
                final ProductData[] dataBuffers = new ProductData[bandList.srcBands.length];
                for (int j = 0; j < bandList.srcBands.length; j++) {
                    sourceTiles[j] = getSourceTile(bandList.srcBands[j], targetRectangle);
                    dataBuffers[j] = sourceTiles[j].getDataBuffer();
                }
                final TileIndex srcIndex = new TileIndex(sourceTiles[0]);
                final TileIndex tgtIndex = new TileIndex(tileDataList[0].tile);

                int srcIdx, tgtIdx;
                for (int y = y0, yy = 0; y < maxY; ++y, ++yy) {
                    srcIndex.calculateStride(y);
                    tgtIndex.calculateStride(y);
                    for (int x = x0, xx = 0; x < maxX; ++x, ++xx) {
                        srcIdx = srcIndex.getIndex(x);
                        tgtIdx = tgtIndex.getIndex(x);

                        if (matrixType.equals(PolBandUtils.MATRIX.C2)) {
                            getScatterVector(srcIdx, dataBuffers, kr, ki);
                            computeCovarianceMatrixC2(kr, ki, tempRe, tempIm);
                        } else {
                            if (matrixType.equals(PolBandUtils.MATRIX.C3)) {
                                getCovarianceMatrixC3(srcIdx, sourceProductType, dataBuffers, tempRe, tempIm);
                            } else if (matrixType.equals(PolBandUtils.MATRIX.C4)) {
                                getCovarianceMatrixC4(srcIdx, sourceProductType, dataBuffers, tempRe, tempIm);
                            } else if (matrixType.equals(PolBandUtils.MATRIX.T3)) {
                                getCoherencyMatrixT3(srcIdx, sourceProductType, dataBuffers, tempRe, tempIm);
                            } else if (matrixType.equals(PolBandUtils.MATRIX.T4)) {
                                getCoherencyMatrixT4(srcIdx, sourceProductType, dataBuffers, tempRe, tempIm);
                            }
                        }

                        for (final TileData tileData : tileDataList) {

                            if (tileData.elem.isImaginary) {
                                tileData.dataBuffer.setElemFloatAt(tgtIdx, (float) tempIm[tileData.elem.i][tileData.elem.j]);
                            } else {
                                tileData.dataBuffer.setElemFloatAt(tgtIdx, (float) tempRe[tileData.elem.i][tileData.elem.j]);
                            }
                        }
                    }
                }

            } catch (Throwable e) {
                OperatorUtils.catchOperatorException(getId(), e);
            }
        }
    }

    private static class MatrixElem {
        public final int i;
        public final int j;
        public final boolean isImaginary;

        MatrixElem(final int i, final int j, final boolean isImaginary) {
            this.i = i;
            this.j = j;
            this.isImaginary = isImaginary;
        }
    }

    private static class TileData {
        final Tile tile;
        final MatrixElem elem;
        final ProductData dataBuffer;

        public TileData(final Tile tile, final MatrixElem elem) {
            this.tile = tile;
            this.elem = elem;
            this.dataBuffer = tile.getDataBuffer();
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.snap.core.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(PolarimetricMatricesOp.class);
        }
    }
}
