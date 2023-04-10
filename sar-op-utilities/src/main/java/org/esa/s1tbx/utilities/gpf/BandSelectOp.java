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
package org.esa.s1tbx.utilities.gpf;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a new product with only selected bands
 */

@OperatorMetadata(alias = "BandSelect",
        category = "Raster/Data Conversion",
        authors = "Jun Lu, Luis Veci",
        version = "1.0",
        copyright = "Copyright (C) 2015 by Array Systems Computing Inc.",
        description = "Creates a new product with only selected bands")
public final class BandSelectOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of polarisations", label = "Polarisations")
    private String[] selectedPolarisations;

    @Parameter(description = "The list of source bands.", alias = "sourceBands",
            rasterDataNodeType = Band.class, label = "Source Bands")
    private String[] sourceBandNames;

    @Parameter(description = "Band name regular expression pattern", label = "Band Name Pattern")
    private String bandNamePattern;

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
            targetProduct = new Product(sourceProduct.getName(),
                    sourceProduct.getProductType(),
                    sourceProduct.getSceneRasterWidth(),
                    sourceProduct.getSceneRasterHeight());

            ProductUtils.copyProductNodes(sourceProduct, targetProduct);

            addSelectedBands();

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private void addSelectedBands() throws OperatorException {

        final Band[] sourceBands = OperatorUtils.getSourceBands(sourceProduct, sourceBandNames, true);

        for (Band srcBand : sourceBands) {
            // check first if polarisation is found
            if (selectedPolarisations != null && selectedPolarisations.length > 0) {
                String pol = OperatorUtils.getPolarizationFromBandName(srcBand.getName());
                boolean foundPol = false;
                for(String selPol : selectedPolarisations) {
                    if(pol.equalsIgnoreCase(selPol)) {
                        foundPol = true;
                    }
                }
                if(!foundPol) {
                    continue;
                }
            }

            if (bandNamePattern != null && !bandNamePattern.isEmpty()) {
                // check regular expression such as contain mst "^.*mst.*$"

                Pattern pattern = Pattern.compile(bandNamePattern);
                Matcher matcher = pattern.matcher(srcBand.getName());
                if (!matcher.matches()) {
                    continue;
                }
            }

            if (targetProduct.containsBand(srcBand.getName())) {
                continue;
            }

            if (srcBand instanceof VirtualBand) {
                ProductUtils.copyVirtualBand(targetProduct, (VirtualBand) srcBand, srcBand.getName());
            } else {
                ProductUtils.copyBand(srcBand.getName(), sourceProduct, targetProduct, true);
            }
        }

        if(targetProduct.getNumBands() == 0) {
            throw new OperatorException("No valid bands found in target product");
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
            super(BandSelectOp.class);
        }
    }
}
