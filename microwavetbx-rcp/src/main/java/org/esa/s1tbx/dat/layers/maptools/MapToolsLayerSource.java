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
package org.esa.s1tbx.dat.layers.maptools;

import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.ui.layer.AbstractLayerSourceAssistantPage;
import org.esa.snap.ui.layer.LayerSource;
import org.esa.snap.ui.layer.LayerSourcePageContext;

/**

 */
public class MapToolsLayerSource implements LayerSource {

    public boolean isApplicable(LayerSourcePageContext pageContext) {
        return true;
    }

    public boolean hasFirstPage() {
        return true;
    }

    public AbstractLayerSourceAssistantPage getFirstPage(LayerSourcePageContext pageContext) {
        return new MapToolsAssistantPage();
    }

    public boolean canFinish(LayerSourcePageContext pageContext) {
        return false;
    }

    public boolean performFinish(LayerSourcePageContext pageContext) {
        return false;
    }

    public void cancel(LayerSourcePageContext pageContext) {
    }

    public static void createLayer(final LayerSourcePageContext pageContext, final MapToolsOptions options) {
        final RasterDataNode raster = SnapApp.getDefault().getSelectedProductSceneView().getRaster();
        final MapToolsLayer geoLayer = MapToolsLayerType.createLayer(raster, options);
        pageContext.getLayerContext().getRootLayer().getChildren().add(0, geoLayer);
    }
}
