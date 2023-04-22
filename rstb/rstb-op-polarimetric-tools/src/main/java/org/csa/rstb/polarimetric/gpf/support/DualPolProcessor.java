package org.csa.rstb.polarimetric.gpf.support;

import eu.esa.sar.commons.polsar.PolBandUtils;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.engine_utilities.gpf.TileIndex;

public interface DualPolProcessor extends PolarimetricProcessor, MatrixMath {

    /**
     * Get mean covariance matrix C2 for given pixel.
     *
     * @param x                 X coordinate of the given pixel.
     * @param y                 Y coordinate of the given pixel.
     * @param halfWindowSizeX   The sliding window width /2
     * @param halfWindowSizeY   The sliding window height /2
     * @param sourceImageWidth  Source image width.
     * @param sourceImageHeight Source image height.
     * @param sourceProductType The source product type.
     * @param sourceTiles       The source tiles for all bands.
     * @param dataBuffers       Source tile data buffers.
     * @param Cr                The real part of the mean covariance matrix.
     * @param Ci                The imaginary part of the mean covariance matrix.
     */
    default void getMeanCovarianceMatrixC2(
            final int x, final int y, final int halfWindowSizeX, final int halfWindowSizeY, final int sourceImageWidth,
            final int sourceImageHeight, final PolBandUtils.MATRIX sourceProductType,
            final Tile[] sourceTiles, final ProductData[] dataBuffers, final double[][] Cr, final double[][] Ci) {

        final int xSt = Math.max(x - halfWindowSizeX, 0);
        final int xEd = Math.min(x + halfWindowSizeX, sourceImageWidth - 1);
        final int ySt = Math.max(y - halfWindowSizeY, 0);
        final int yEd = Math.min(y + halfWindowSizeY, sourceImageHeight - 1);
        final int num = (yEd - ySt + 1) * (xEd - xSt + 1);

        final TileIndex srcIndex = new TileIndex(sourceTiles[0]);

        final double[][] crMat = new double[2][2];
        final double[][] ciMat = new double[2][2];

        final double[][] tmpCr = new double[2][2];
        final double[][] tmpCi = new double[2][2];

        if (sourceProductType == PolBandUtils.MATRIX.C2) {

            for (int yy = ySt; yy <= yEd; ++yy) {
                srcIndex.calculateStride(yy);
                for (int xx = xSt; xx <= xEd; ++xx) {
                    getCovarianceMatrixC2(srcIndex.getIndex(xx), dataBuffers, tmpCr, tmpCi);
                    matrixPlusEquals(crMat, tmpCr);
                    matrixPlusEquals(ciMat, tmpCi);
                }
            }

        } else if (sourceProductType == PolBandUtils.MATRIX.LCHCP ||
                sourceProductType == PolBandUtils.MATRIX.RCHCP ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_HV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_VH_VV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_VV) {

            final double[] tempKr = new double[2];
            final double[] tempKi = new double[2];

            for (int yy = ySt; yy <= yEd; ++yy) {
                srcIndex.calculateStride(yy);
                for (int xx = xSt; xx <= xEd; ++xx) {
                    getScatterVector(srcIndex.getIndex(xx), dataBuffers, tempKr, tempKi);
                    computeCovarianceMatrixC2(tempKr, tempKi, tmpCr, tmpCi);
                    matrixPlusEquals(crMat, tmpCr);
                    matrixPlusEquals(ciMat, tmpCi);
                }
            }

        } else {
            throw new OperatorException("getMeanCovarianceMatrixC2 not implemented for raw dual pol");
        }

        matrixTimesEquals(crMat, 1.0 / num);
        matrixTimesEquals(ciMat, 1.0 / num);

        Cr[0][0] = crMat[0][0];
        Ci[0][0] = ciMat[0][0];
        Cr[0][1] = crMat[0][1];
        Ci[0][1] = ciMat[0][1];

        Cr[1][0] = crMat[1][0];
        Ci[1][0] = ciMat[1][0];
        Cr[1][1] = crMat[1][1];
        Ci[1][1] = ciMat[1][1];
    }

    /**
     * Get covariance matrix C2 for a given pixel in the input C2 product.
     *
     * @param index       X,Y coordinate of the given pixel
     * @param dataBuffers Source tile data buffers for all 4 source bands
     * @param Cr          Real part of the 2x2 covariance matrix
     * @param Ci          Imaginary part of the 2x2 covariance matrix
     */
    default void getCovarianceMatrixC2(final int index, final ProductData[] dataBuffers,
                                       final double[][] Cr, final double[][] Ci) {

        Cr[0][0] = dataBuffers[0].getElemDoubleAt(index); // C11 - real
        Ci[0][0] = 0.0;                                   // C11 - imag

        Cr[0][1] = dataBuffers[1].getElemDoubleAt(index); // C12 - real
        Ci[0][1] = dataBuffers[2].getElemDoubleAt(index); // C12 - imag

        Cr[1][1] = dataBuffers[3].getElemDoubleAt(index); // C22 - real
        Ci[1][1] = 0.0;                                   // C22 - imag

        Cr[1][0] = Cr[0][1];
        Ci[1][0] = -Ci[0][1];
    }

    /**
     * Get covariance matrix C2 for a given pixel.
     *
     * @param index       X,Y coordinate of the given pixel
     * @param dataBuffers Source tile data buffers for all 4 source bands
     * @param Cr          Real part of the 2x2 covariance matrix
     * @param Ci          Imaginary part of the 2x2 covariance matrix
     */
    default void getCovarianceMatrixC2(final int index, final PolBandUtils.MATRIX sourceProductType,
                                       final ProductData[] dataBuffers, final double[][] Cr,
                                       final double[][] Ci) {

        if (sourceProductType == PolBandUtils.MATRIX.LCHCP ||
                sourceProductType == PolBandUtils.MATRIX.RCHCP ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_HV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_VH_VV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_VV) {

            final double[] kr = new double[2];
            final double[] ki = new double[2];
            getScatterVector(index, dataBuffers, kr, ki);
            computeCovarianceMatrixC2(kr, ki, Cr, Ci);

        } else if (sourceProductType == PolBandUtils.MATRIX.C2) {
            getCovarianceMatrixC2(index, dataBuffers, Cr, Ci);
        }
    }

    /**
     * Get compact-pol or dual-pol scatter vector for a given pixel in the input product.
     *
     * @param index       X,Y coordinate of the given pixel
     * @param dataBuffers Source tiles dataBuffers for all 4 source bands
     * @param kr          Real part of the scatter vector
     * @param ki          Imaginary part of the scatter vector
     */
    default void getScatterVector(final int index, final ProductData[] dataBuffers,
                                  final double[] kr, final double[] ki) {

        kr[0] = dataBuffers[0].getElemDoubleAt(index);
        ki[0] = dataBuffers[1].getElemDoubleAt(index);

        kr[1] = dataBuffers[2].getElemDoubleAt(index);
        ki[1] = dataBuffers[3].getElemDoubleAt(index);
    }

    /**
     * Compute covariance matrix c2 for given dual pol or complex compact pol 2x1 scatter vector.
     * <p>
     * For dual pol product:
     * <p>
     * Case 1) k_DP1 = [S_HH
     * S_HV]
     * kr[0] = i_hh, ki[0] = q_hh, kr[1] = i_hv, ki[1] = q_hv
     * <p>
     * Case 2) k_DP2 = [S_VH
     * S_VV]
     * kr[0] = i_vh, ki[0] = q_vh, kr[1] = i_vv, ki[1] = q_vv
     * <p>
     * Case 3) k_DP3 = [S_HH
     * S_VV]
     * kr[0] = i_hh, ki[0] = q_hh, kr[1] = i_vv, ki[1] = q_vv
     *
     * @param kr Real part of the scatter vector
     * @param ki Imaginary part of the scatter vector
     * @param Cr Real part of the covariance matrix
     * @param Ci Imaginary part of the covariance matrix
     */
    default void computeCovarianceMatrixC2(final double[] kr, final double[] ki,
                                           final double[][] Cr, final double[][] Ci) {

        Cr[0][0] = kr[0] * kr[0] + ki[0] * ki[0];
        Ci[0][0] = 0.0;

        Cr[0][1] = kr[0] * kr[1] + ki[0] * ki[1];
        Ci[0][1] = ki[0] * kr[1] - kr[0] * ki[1];

        Cr[1][1] = kr[1] * kr[1] + ki[1] * ki[1];
        Ci[1][1] = 0.0;

        Cr[1][0] = Cr[0][1];
        Ci[1][0] = -Ci[0][1];
    }

    default void getMeanCorrelationMatrixC2(
            final int x, final int y, final int halfWindowSizeX, final int halfWindowSizeY,
            final int sourceImageWidth, final int sourceImageHeight, final PolBandUtils.MATRIX sourceProductType,
            final Tile[] sourceTiles, final ProductData[] mstDataBuffers, final ProductData[] slvDataBuffers,
            final double[][] Cr, final double[][] Ci) {

        final int xSt = Math.max(x - halfWindowSizeX, 0);
        final int xEd = Math.min(x + halfWindowSizeX, sourceImageWidth - 1);
        final int ySt = Math.max(y - halfWindowSizeY, 0);
        final int yEd = Math.min(y + halfWindowSizeY, sourceImageHeight - 1);
        final int num = (yEd - ySt + 1) * (xEd - xSt + 1);

        final TileIndex srcIndex = new TileIndex(sourceTiles[0]);

        final double[][] crMat = new double[2][2];
        final double[][] ciMat = new double[2][2];
        final double[][] tmpCrMat = new double[2][2];
        final double[][] tmpCiMat = new double[2][2];

        if (sourceProductType == PolBandUtils.MATRIX.LCHCP ||
                sourceProductType == PolBandUtils.MATRIX.RCHCP ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_HV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_VH_VV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_VV) {

            final double[] K1r = new double[2];
            final double[] K1i = new double[2];
            final double[] K2r = new double[2];
            final double[] K2i = new double[2];

            for (int yy = ySt; yy <= yEd; ++yy) {
                srcIndex.calculateStride(yy);
                for (int xx = xSt; xx <= xEd; ++xx) {
                    final int idx = srcIndex.getIndex(xx);

                    getScatterVector(idx, mstDataBuffers, K1r, K1i);
                    getScatterVector(idx, slvDataBuffers, K2r, K2i);

                    computeCorrelationMatrixC2(K1r, K1i, K2r, K2i, tmpCrMat, tmpCiMat);

                    matrixPlusEquals(crMat, tmpCrMat);
                    matrixPlusEquals(ciMat, tmpCiMat);
                }
            }

        } else {
            throw new OperatorException("getMeanCorrelationMatrix: input should be raw dual pol data");
        }

        matrixTimesEquals(crMat, 1.0 / num);
        matrixTimesEquals(ciMat, 1.0 / num);

        Cr[0][0] = crMat[0][0];
        Ci[0][0] = ciMat[0][0];
        Cr[0][1] = crMat[0][1];
        Ci[0][1] = ciMat[0][1];

        Cr[1][0] = crMat[1][0];
        Ci[1][0] = ciMat[1][0];
        Cr[1][1] = crMat[1][1];
        Ci[1][1] = ciMat[1][1];
    }

    default void computeCorrelationMatrixC2(final double[] k1r, final double[] k1i,
                                            final double[] k2r, final double[] k2i,
                                            final double[][] Cr, final double[][] Ci) {

        Cr[0][0] = k1r[0] * k2r[0] + k1i[0] * k2i[0];
        Ci[0][0] = k1i[0] * k2r[0] - k1r[0] * k2i[0];

        Cr[0][1] = k1r[0] * k2r[1] + k1i[0] * k2i[1];
        Ci[0][1] = k1i[0] * k2r[1] - k1r[0] * k2i[1];

        Cr[1][0] = k1r[1] * k2r[0] + k1i[1] * k2i[0];
        Ci[1][0] = k1i[1] * k2r[0] - k1r[1] * k2i[0];

        Cr[1][1] = k1r[1] * k2r[1] + k1i[1] * k2i[1];
        Ci[1][1] = k1i[1] * k2r[1] - k1r[1] * k2i[1];
    }
}
