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
package org.esa.s1tbx.sentinel1.rcp.layers.topsbursts;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.binding.accessors.DefaultPropertyAccessor;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerContext;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glayer.LayerTypeRegistry;
import com.bc.ceres.glayer.annotations.LayerTypeMetadata;
import org.esa.snap.core.datamodel.RasterDataNode;

/**

 */
@LayerTypeMetadata(name = "TOPSBurstLayerType",
        aliasNames = {"TOPSBurstLayerType"})
public class TOPSBurstLayerType extends LayerType {

    private static final String TYPE_NAME = "TOPSBurstLayerType";
    private static final String[] ALIASES = {"TOPSBurstLayerType"};

    public static TOPSBurstsLayer createLayer(final RasterDataNode raster) {
        final LayerType type = LayerTypeRegistry.getLayerType(TOPSBurstLayerType.class);
        final PropertySet template = type.createLayerConfig(null);
        template.setValue("raster", raster);
        return new TOPSBurstsLayer(type, template);
    }

    @Override
    public String getName() {
        return TYPE_NAME;
    }

    @Override
    public String[] getAliases() {
        return ALIASES;
    }

    @Override
    public boolean isValidFor(LayerContext ctx) {
        return true;
    }

    @Override
    public Layer createLayer(LayerContext ctx, PropertySet configuration) {
        return new TOPSBurstsLayer(this, configuration);
    }

    @Override
    public PropertySet createLayerConfig(LayerContext ctx) {
        final PropertyContainer propertyContainer = new PropertyContainer();
        propertyContainer.addProperty(new Property(new PropertyDescriptor("raster", RasterDataNode.class),
                new DefaultPropertyAccessor()));
        return propertyContainer;
    }
}
