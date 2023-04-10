/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.s1tbx.io.generic;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s1tbx.commons.io.FileImageInputStreamExtImpl;
import org.esa.s1tbx.dat.dialogs.GenericBinaryDialog;
import org.esa.s1tbx.io.binary.ArrayCopy;
import org.esa.s1tbx.io.binary.BinaryFileReader;
import org.esa.s1tbx.io.binary.IllegalBinaryFormatException;
import org.esa.snap.core.dataio.AbstractProductReader;
import org.esa.snap.core.dataio.DecodeQualification;
import org.esa.snap.core.dataio.ProductReaderPlugIn;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.datamodel.metadata.AbstractMetadataIO;
import org.esa.snap.engine_utilities.gpf.ReaderUtils;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.ui.ModalDialog;

import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * The product reader for ImageIO products.
 */
public class GenericReader extends AbstractProductReader {

    private int rasterWidth = 0;
    private int rasterHeight = 0;
    private int numBands = 1;
    private int dataType = ProductData.TYPE_INT16;
    private ByteOrder byteOrder = ByteOrder.nativeOrder();

    private int imageRecordLength = rasterWidth;
    private final int _startPosImageRecords = 0;
    private int _imageHeaderLength = 0;
    private ImageInputStream imageInputStream = null;

    private BinaryFileReader binaryReader = null;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public GenericReader(final ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    /**
     * Provides an implementation of the <code>readProductNodes</code> interface method. Clients implementing this
     * method can be sure that the input object and eventually the subset information has already been set.
     * <p/>
     * <p>This method is called as a last step in the <code>readProductNodes(input, subsetInfo)</code> method.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    protected Product readProductNodesImpl() throws IOException {
        if (SnapApp.getDefault() != null) {
            //if in DAT then open options dialog
            final GenericBinaryDialog dialog = new GenericBinaryDialog(SnapApp.getDefault().getMainFrame(), "importGenericBinary");
            if (dialog.show() == ModalDialog.ID_OK) {
                rasterWidth = dialog.getRasterWidth();
                rasterHeight = dialog.getRasterHeight();
                numBands = dialog.getNumBands();
                dataType = dialog.getDataType();
                byteOrder = dialog.getByteOrder();
                _imageHeaderLength = dialog.getHeaderBytes();
                imageRecordLength = getImageRecordLength(rasterWidth, dataType);
            } else {
                throw new IOException("Import Canceled");
            }
        }

        final Path inputPath = ReaderUtils.getPathFromInput(getInput());
        final File inputFile = inputPath.toFile();

        final Product product = new Product(inputPath.getFileName().toString(),
                "Generic", rasterWidth, rasterHeight);
        product.setFileLocation(inputFile);

        int bandCnt = 1;
        for (int b = 0; b < numBands; ++b) {
            final Band band = new Band("band" + bandCnt++, dataType, rasterWidth, rasterHeight);
            product.addBand(band);
        }

        addMetaData(product, inputFile);

        product.getGcpGroup();
        product.setProductReader(this);
        product.setModified(false);

        imageInputStream = FileImageInputStreamExtImpl.createInputStream(inputFile);
        imageInputStream.setByteOrder(byteOrder);
        binaryReader = new BinaryFileReader(imageInputStream);

        return product;
    }

    private static int getImageRecordLength(final int rasterWidth, final int dataType) {

        return rasterWidth * ProductData.getElemSize(dataType);
    }

    @Override
    public void close() throws IOException {
        super.close();

        binaryReader.close();
    }

    static DecodeQualification checkProductQualification(File file) {
        return DecodeQualification.SUITABLE;
    }

    private static void addMetaData(final Product product, final File inputFile) throws IOException {
        final MetadataElement root = product.getMetadataRoot();
        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, product.getName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, product.getProductType());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line, product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines, product.getSceneRasterHeight());

        AbstractMetadataIO.loadExternalMetadata(product, absRoot, inputFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        readBandRasterData(sourceOffsetX, sourceOffsetY,
                sourceWidth, sourceHeight,
                sourceStepX, sourceStepY,
                _startPosImageRecords + _imageHeaderLength, imageInputStream,
                destBand, destWidth, destBuffer, pm);
    }

    private static void readBandRasterData(final int sourceOffsetX, final int sourceOffsetY,
                                           final int sourceWidth, final int sourceHeight,
                                           final int sourceStepX, final int sourceStepY,
                                           final long bandOffset, final ImageInputStream imageInputStream,
                                           final Band destBand, final int destWidth, final ProductData destBuffer,
                                           final ProgressMonitor pm) throws IOException {

        final int sourceMinX = sourceOffsetX;
        final int sourceMinY = sourceOffsetY;
        final int sourceMaxX = sourceOffsetX + sourceWidth - 1;
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;

        final int sourceRasterWidth = destBand.getProduct().getSceneRasterWidth();

        final int elemSize = destBuffer.getElemSize();
        int destPos = 0;

        pm.beginTask("Reading band '" + destBand.getName() + "'...", sourceMaxY - sourceMinY);
        try {
            for (int sourceY = sourceMinY; sourceY <= sourceMaxY; sourceY += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }
                final long sourcePosY = sourceY * sourceRasterWidth;
                synchronized (imageInputStream) {
                    if (sourceStepX == 1) {
                        imageInputStream.seek(bandOffset + elemSize * (sourcePosY + sourceMinX));
                        destBuffer.readFrom(destPos, destWidth, imageInputStream);
                        destPos += destWidth;
                    } else {
                        for (int sourceX = sourceMinX; sourceX <= sourceMaxX; sourceX += sourceStepX) {
                            imageInputStream.seek(bandOffset + elemSize * (sourcePosY + sourceX));
                            destBuffer.readFrom(destPos, 1, imageInputStream);
                            destPos++;
                        }
                    }
                }
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataIntSLC(final int sourceOffsetX, final int sourceOffsetY,
                                                final int sourceWidth, final int sourceHeight,
                                                final int sourceStepX, final int sourceStepY,
                                                final long bandOffset, boolean oneOf2, final ImageInputStream imageInputStream,
                                                final Band destBand, final int destWidth, final ProductData destBuffer,
                                                final ProgressMonitor pm) throws IOException {

        final int sourceMinX = sourceOffsetX;
        final int sourceMinY = sourceOffsetY;
        final int sourceMaxX = sourceOffsetX + sourceWidth - 1;
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;

        final int sourceRasterWidth = destBand.getProduct().getSceneRasterWidth();

        final int elemSize = destBuffer.getElemSize();
        int destPos = 0;

        pm.beginTask("Reading band '" + destBand.getName() + "'...", sourceMaxY - sourceMinY);
        try {
            final int[] srcLine = new int[sourceWidth * 2];
            final int[] destLine = new int[destWidth];

            for (int sourceY = sourceMinY; sourceY <= sourceMaxY; sourceY += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }
                final int currentLineIndex = (sourceY - sourceOffsetY) * destWidth;
                final long sourcePosY = sourceY * sourceRasterWidth;
                synchronized (imageInputStream) {
                    imageInputStream.seek(bandOffset + elemSize * (sourcePosY + sourceMinX));
                    imageInputStream.readFully(srcLine, 0, srcLine.length);
                }

                if (oneOf2)
                    ArrayCopy.copyLine1Of2(srcLine, destLine, sourceStepX);
                else
                    ArrayCopy.copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataShort(final int sourceOffsetX, final int sourceOffsetY,
                                               final int sourceWidth, final int sourceHeight,
                                               final int sourceStepX, final int sourceStepY,
                                               final int imageStartOffset, int imageRecordLength,
                                               final int destWidth, final ProductData destBuffer,
                                               final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 2;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth];
            short[] destLine = null;
            if (sourceStepX != 1)
                destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataUShort(final int sourceOffsetX, final int sourceOffsetY,
                                                final int sourceWidth, final int sourceHeight,
                                                final int sourceStepX, final int sourceStepY,
                                                final int imageStartOffset, int imageRecordLength,
                                                final int destWidth, final ProductData destBuffer,
                                                final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final char[] srcLine = new char[sourceWidth];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataByte(final int sourceOffsetX, final int sourceOffsetY,
                                              final int sourceWidth, final int sourceHeight,
                                              final int sourceStepX, final int sourceStepY,
                                              final int imageStartOffset, int imageRecordLength,
                                              final int destWidth, final ProductData destBuffer,
                                              final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 1;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final byte[] srcLine = new byte[sourceWidth];
            byte[] destLine = null;
            if (sourceStepX != 1)
                destLine = new byte[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataUByte(final int sourceOffsetX, final int sourceOffsetY,
                                               final int sourceWidth, final int sourceHeight,
                                               final int sourceStepX, final int sourceStepY,
                                               final int imageStartOffset, int imageRecordLength,
                                               final int destWidth, final ProductData destBuffer,
                                               final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException, IllegalBinaryFormatException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 2;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final byte[] srcLine = new byte[sourceWidth];
            byte[] destLine = null;
            if (sourceStepX != 1)
                destLine = new byte[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    //binaryReader.read(srcLine);
                    for (int i = 0; i < sourceWidth; ++i) {
                        srcLine[i] = (byte) binaryReader.readUB1();
                    }
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }


    public static void readBandRasterDataInt(final int sourceOffsetX, final int sourceOffsetY,
                                             final int sourceWidth, final int sourceHeight,
                                             final int sourceStepX, final int sourceStepY,
                                             final int imageStartOffset, int imageRecordLength,
                                             final int destWidth, final ProductData destBuffer,
                                             final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final int[] srcLine = new int[sourceWidth];
            int[] destLine = null;
            if (sourceStepX != 1)
                destLine = new int[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataFloat(final int sourceOffsetX, final int sourceOffsetY,
                                               final int sourceWidth, final int sourceHeight,
                                               final int sourceStepX, final int sourceStepY,
                                               final int imageStartOffset, int imageRecordLength,
                                               final int destWidth, final ProductData destBuffer,
                                               final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final float[] srcLine = new float[sourceWidth];
            float[] destLine = null;
            if (sourceStepX != 1)
                destLine = new float[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataLong(final int sourceOffsetX, final int sourceOffsetY,
                                              final int sourceWidth, final int sourceHeight,
                                              final int sourceStepX, final int sourceStepY,
                                              final int imageStartOffset, int imageRecordLength,
                                              final int destWidth, final ProductData destBuffer,
                                              final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 8;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final long[] srcLine = new long[sourceWidth];
            long[] destLine = null;
            if (sourceStepX != 1)
                destLine = new long[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataDouble(final int sourceOffsetX, final int sourceOffsetY,
                                                final int sourceWidth, final int sourceHeight,
                                                final int sourceStepX, final int sourceStepY,
                                                final int imageStartOffset, int imageRecordLength,
                                                final int destWidth, final ProductData destBuffer,
                                                final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 8;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final double[] srcLine = new double[sourceWidth];
            double[] destLine = null;
            if (sourceStepX != 1)
                destLine = new double[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (sourceStepX == 1) {

                    System.arraycopy(srcLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                } else {
                    ArrayCopy.copyLine(srcLine, destLine, sourceStepX);

                    System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataSLCShort(final int sourceOffsetX, final int sourceOffsetY,
                                                  final int sourceWidth, final int sourceHeight,
                                                  final int sourceStepX, final int sourceStepY,
                                                  final int imageStartOffset, int imageRecordLength,
                                                  final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                                  final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 4;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth * 2];
            final short[] destLine = new short[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (oneOf2)
                    ArrayCopy.copyLine1Of2(srcLine, destLine, sourceStepX);
                else
                    ArrayCopy.copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataSLCFloat(final int sourceOffsetX, final int sourceOffsetY,
                                                  final int sourceWidth, final int sourceHeight,
                                                  final int sourceStepX, final int sourceStepY,
                                                  final int imageStartOffset, int imageRecordLength,
                                                  final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                                  final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 8;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final float[] srcLine = new float[sourceWidth * 2];
            final float[] destLine = new float[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (oneOf2)
                    ArrayCopy.copyLine1Of2(srcLine, destLine, sourceStepX);
                else
                    ArrayCopy.copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }

    public static void readBandRasterDataSLCByte(final int sourceOffsetX, final int sourceOffsetY,
                                                 final int sourceWidth, final int sourceHeight,
                                                 final int sourceStepX, final int sourceStepY,
                                                 final int imageStartOffset, int imageRecordLength,
                                                 final int destWidth, final ProductData destBuffer, boolean oneOf2,
                                                 final BinaryFileReader binaryReader, final ProgressMonitor pm)
            throws IOException {
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX * 2;
        final long xpos = imageStartOffset + x;

        pm.beginTask("Reading band...", sourceMaxY - sourceOffsetY);
        try {
            final byte[] srcLine = new byte[sourceWidth * 2];
            final byte[] destLine = new byte[destWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; y += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }

                // Read source line
                synchronized (binaryReader) {
                    binaryReader.seek(imageRecordLength * y + xpos);
                    binaryReader.read(srcLine);
                }

                // Copy source line into destination buffer
                final int currentLineIndex = (y - sourceOffsetY) * destWidth;
                if (oneOf2)
                    ArrayCopy.copyLine1Of2(srcLine, destLine, sourceStepX);
                else
                    ArrayCopy.copyLine2Of2(srcLine, destLine, sourceStepX);

                System.arraycopy(destLine, 0, destBuffer.getElems(), currentLineIndex, destWidth);

                pm.worked(1);
            }

        } finally {
            pm.done();
        }
    }
}
