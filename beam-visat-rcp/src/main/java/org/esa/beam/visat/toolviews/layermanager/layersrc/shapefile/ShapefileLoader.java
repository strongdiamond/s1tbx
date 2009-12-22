package org.esa.beam.visat.toolviews.layermanager.layersrc.shapefile;

import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glayer.LayerTypeRegistry;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.layer.LayerSourcePageContext;
import org.esa.beam.util.FeatureCollectionClipper;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Fill;
import org.geotools.styling.LineSymbolizer;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.PolygonSymbolizer;
import org.geotools.styling.Rule;
import org.geotools.styling.SLD;
import org.geotools.styling.SLDParser;
import org.geotools.styling.Style;
import org.geotools.styling.Symbolizer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.FilterFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

/**
 * @author Marco Peters
 * @version $ Revision $ Date $
 * @since BEAM 4.6
 */
class ShapefileLoader extends ProgressMonitorSwingWorker<Layer, Object> {

    private static final org.geotools.styling.StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory(null);
    private static final FilterFactory filterFactory = CommonFactoryFinder.getFilterFactory(null);

    private final LayerSourcePageContext context;

    ShapefileLoader(LayerSourcePageContext context) {
        super(context.getWindow(), "Loading Shapefile");
        this.context = context;
    }

    protected LayerSourcePageContext getContext() {
        return context;
    }

    @Override
    protected Layer doInBackground(ProgressMonitor pm) throws Exception {

        try {
            pm.beginTask("Reading shapes", ProgressMonitor.UNKNOWN);
            final ProductSceneView sceneView = context.getAppContext().getSelectedProductSceneView();
            CoordinateReferenceSystem targetCrs = (CoordinateReferenceSystem) context.getLayerContext().getCoordinateReferenceSystem();
            final Geometry clipGeometry = FeatureCollectionClipper.createGeoBoundaryPolygon(sceneView.getProduct());

            File file = new File((String) context.getPropertyValue(ShapefileLayerSource.PROPERTY_NAME_FILE_PATH));
            Object featureCollectionValue = context.getPropertyValue(ShapefileLayerSource.PROPERTY_NAME_FEATURE_COLLECTION);
            FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection;
            if (featureCollectionValue == null) {
                featureCollection = ShapefileUtils.createFeatureCollection(file.toURI().toURL(), targetCrs, clipGeometry);
            } else {
                featureCollection = (FeatureCollection<SimpleFeatureType, SimpleFeature>) featureCollectionValue;
            }

            Style[] styles = getStyles(file, featureCollection);
            Style selectedStyle = getSelectedStyle(styles);

            final LayerType type = LayerTypeRegistry.getLayerType(FeatureLayerType.class.getName());
            final PropertySet configuration = type.createLayerConfig(sceneView);
            configuration.setValue(FeatureLayerType.PROPERTY_NAME_FEATURE_COLLECTION_URL, file.toURI().toURL());
            configuration.setValue(FeatureLayerType.PROPERTY_NAME_FEATURE_COLLECTION_CRS, targetCrs);
            configuration.setValue(FeatureLayerType.PROPERTY_NAME_FEATURE_COLLECTION_CLIP_GEOMETRY, clipGeometry);
            configuration.setValue(FeatureLayerType.PROPERTY_NAME_FEATURE_COLLECTION, featureCollection);
            configuration.setValue(FeatureLayerType.PROPERTY_NAME_SLD_STYLE, selectedStyle);
            Layer featureLayer = type.createLayer(sceneView, configuration);
            featureLayer.setName(file.getName());
            featureLayer.setVisible(true);
            return featureLayer;
        } finally {
            pm.done();
        }
    }

    private Style getSelectedStyle(Style[] styles) {
        Style selectedStyle;
        selectedStyle = (Style) context.getPropertyValue(ShapefileLayerSource.PROPERTY_NAME_SELECTED_STYLE);
        if (selectedStyle == null) {
            selectedStyle = styles[0];
            context.setPropertyValue(ShapefileLayerSource.PROPERTY_NAME_SELECTED_STYLE, styles[0]);
        }
        return selectedStyle;
    }

    private Style[] getStyles(File file, FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {
        Style[] styles;
        styles = (Style[]) context.getPropertyValue(ShapefileLayerSource.PROPERTY_NAME_STYLES);
        if (styles == null) {
            styles = createStyle(file, featureCollection.getSchema());
            context.setPropertyValue(ShapefileLayerSource.PROPERTY_NAME_STYLES, styles);
        }
        return styles;
    }

    public static Style[] createStyle(File file, FeatureType schema) {
        File sld = toSLDFile(file);
        if (sld.exists()) {
            final Style[] styles = createFromSLD(sld);
            if (styles.length > 0) {
                return styles;
            }
        }
        Class<?> type = schema.getGeometryDescriptor().getType().getBinding();
        if (type.isAssignableFrom(Polygon.class)
            || type.isAssignableFrom(MultiPolygon.class)) {
            return new Style[]{createPolygonStyle()};
        } else if (type.isAssignableFrom(LineString.class)
                   || type.isAssignableFrom(MultiLineString.class)) {
            return new Style[]{createLineStyle()};
        } else {
            return new Style[]{createPointStyle()};
        }
    }// Figure out the URL for the "sld" file

    private static File toSLDFile(File file) {
        String filename = file.getAbsolutePath();
        if (filename.endsWith(".shp") || filename.endsWith(".dbf")
            || filename.endsWith(".shx")) {
            filename = filename.substring(0, filename.length() - 4);
            filename += ".sld";
        } else if (filename.endsWith(".SHP") || filename.endsWith(".DBF")
                   || filename.endsWith(".SHX")) {
            filename = filename.substring(0, filename.length() - 4);
            filename += ".SLD";
        }
        return new File(filename);
    }

    private static Style[] createFromSLD(File sld) {
        try {
            SLDParser stylereader = new SLDParser(styleFactory, sld.toURI().toURL());
            return stylereader.readXML();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Style[0];
    }

    private static Style createPointStyle() {
        PointSymbolizer symbolizer = styleFactory.createPointSymbolizer();
        symbolizer.getGraphic().setSize(filterFactory.literal(1));

        Rule rule = styleFactory.createRule();
        rule.setSymbolizers(new Symbolizer[]{symbolizer});
        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle();
        fts.setRules(new Rule[]{rule});

        Style style = styleFactory.createStyle();
        style.addFeatureTypeStyle(fts);
        return style;
    }

    private static Style createLineStyle() {
        LineSymbolizer symbolizer = styleFactory.createLineSymbolizer();
        SLD.setLineColour(symbolizer, Color.BLUE);
        symbolizer.getStroke().setWidth(filterFactory.literal(1));
        symbolizer.getStroke().setColor(filterFactory.literal(Color.BLUE));

        Rule rule = styleFactory.createRule();
        rule.setSymbolizers(new Symbolizer[]{symbolizer});
        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle();
        fts.setRules(new Rule[]{rule});

        Style style = styleFactory.createStyle();
        style.addFeatureTypeStyle(fts);
        return style;
    }

    private static Style createPolygonStyle() {
        PolygonSymbolizer symbolizer = styleFactory.createPolygonSymbolizer();
        Fill fill = styleFactory.createFill(
                filterFactory.literal("#FFAA00"),
                filterFactory.literal(0.5)
        );
        symbolizer.setFill(fill);
        Rule rule = styleFactory.createRule();
        rule.setSymbolizers(new Symbolizer[]{symbolizer});
        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle();
        fts.setRules(new Rule[]{rule});

        Style style = styleFactory.createStyle();
        style.addFeatureTypeStyle(fts);
        return style;
    }
}
