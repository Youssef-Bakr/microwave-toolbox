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
package org.esa.s1tbx.insar.rcp.layersrc;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.binding.accessors.DefaultPropertyAccessor;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerContext;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glayer.LayerTypeRegistry;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;

/**
 * The type descriptor of the {@link GCPVectorLayer}.
 */
public class GCPVectorLayerType extends LayerType {

    public static GCPVectorLayer createLayer(final Product product, final Band band) {
        final LayerType type = LayerTypeRegistry.getLayerType(GCPVectorLayerType.class);
        final PropertySet template = type.createLayerConfig(null);
        template.setValue("product", product);
        template.setValue("band", band);
        return new GCPVectorLayer(template);
    }

    @Override
    public boolean isValidFor(LayerContext ctx) {
        return true;
    }

    @Override
    public Layer createLayer(LayerContext ctx, PropertySet configuration) {
        return new GCPVectorLayer(configuration);
    }

    @Override
    public PropertyContainer createLayerConfig(LayerContext ctx) {
        final PropertyContainer valueContainer = new PropertyContainer();
        valueContainer.addProperty(new Property(new PropertyDescriptor("product", Product.class), new DefaultPropertyAccessor()));
        valueContainer.addProperty(new Property(new PropertyDescriptor("band", Band.class), new DefaultPropertyAccessor()));
        return valueContainer;
    }
}
