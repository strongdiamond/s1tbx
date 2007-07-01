/*
 * $Id: SpectrumToolView.java,v 1.1 2007/04/19 10:41:38 norman Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.visat.toolviews.spectrum;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.DataNode;
import org.esa.beam.framework.datamodel.Pin;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeEvent;
import org.esa.beam.framework.datamodel.ProductNodeListenerAdapter;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.PixelPositionListener;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.framework.ui.diagram.DiagramCanvas;
import org.esa.beam.framework.ui.diagram.DiagramGraph;
import org.esa.beam.framework.ui.diagram.DiagramGraphIO;
import org.esa.beam.framework.ui.diagram.DiagramGraphStyle;
import org.esa.beam.framework.ui.diagram.DefaultDiagramGraphStyle;
import org.esa.beam.framework.ui.product.BandChooser;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.util.Debug;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.util.io.BeamFileChooser;
import org.esa.beam.util.io.BeamFileFilter;
import org.esa.beam.visat.VisatApp;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Paint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A window which displays product spectra.
 */
public class SpectrumToolView extends AbstractToolView {

    public static final String ID = SpectrumToolView.class.getName();
    private static final String SUPPRESS_MESSAGE_KEY = "plugin.spectrum.tip";

    private static final String MSG_NO_SPECTRAL_BANDS = "No spectral bands.";   /*I18N*/

    private Map<Product, CursorSpectrumPPL> cursorSpectrumPPLMap;
    private ProductSceneView currentView;
    private Product currentProduct;
    private final HashMap<Product, SpectraDiagram> productToDiagramMap;
    private final ProductNodeListenerAdapter productNodeHandler;
    private DiagramCanvas diagramCanvas;
    private AbstractButton filterButton;
    private boolean tipShown;
    private String originalDescriptorTitle;
    private AbstractButton showSpectraForSelectedPinsButton;
    private AbstractButton showSpectraForAllPinsButton;
    private AbstractButton showSpectrumForCursorButton;
    private AbstractButton showAveragePinSpectrumButton;
    private int pixelX;
    private int pixelY;

    public SpectrumToolView() {
        productNodeHandler = new ProductNodeHandler();
        productToDiagramMap = new HashMap<Product, SpectraDiagram>(4);
        cursorSpectrumPPLMap = new HashMap<Product, CursorSpectrumPPL>(4);
    }

    public ProductSceneView getCurrentView() {
        return currentView;
    }

    public void setCurrentView(ProductSceneView view) {
        if (originalDescriptorTitle != null) {
            originalDescriptorTitle = getDescriptor().getTitle();
        }
        ProductSceneView oldView = currentView;
        currentView = view;
        if (oldView != currentView) {
            if (currentView != null) {
                setCurrentProduct(currentView.getProduct());
            }
            updateUIState();
        }
    }

    public Product getCurrentProduct() {
        return currentProduct;
    }

    public void setCurrentProduct(Product product) {
        Product oldProduct = currentProduct;
        currentProduct = product;
        if (currentProduct != oldProduct) {
            if (oldProduct != null) {
                oldProduct.removeProductNodeListener(productNodeHandler);
            }
            if (currentProduct != null) {
                currentProduct.addProductNodeListener(productNodeHandler);
                SpectraDiagram spectraDiagram = getSpectraDiagram();
                if (spectraDiagram != null) {
                    diagramCanvas.setDiagram(spectraDiagram);
                } else {
                    recreateSpectraDiagram();
                }
            }
            if (currentProduct == null) {
                diagramCanvas.setMessageText("No product selected."); /*I18N*/
            } else {
                diagramCanvas.setMessageText(null);
            }
            updateUIState();
            updateTitle();
        }
    }

    private void updateTitle() {
        if (currentProduct != null) {
            setTitle(getDescriptor().getTitle() + " - " + currentView.getProduct().getProductRefString());
        } else {
            setTitle(getDescriptor().getTitle());
        }
    }

    private void updateUIState() {
        boolean hasView = getCurrentView() != null;
        boolean hasProduct = getCurrentProduct() != null;
        boolean hasSelectedPins = getCurrentProduct() != null && getCurrentProduct().getSelectedPin() != null;
        boolean hasPins = getCurrentProduct() != null && getCurrentProduct().getNumPins() > 0;

        filterButton.setEnabled(hasProduct);
        showSpectrumForCursorButton.setEnabled(hasView);
        showSpectraForSelectedPinsButton.setEnabled(hasSelectedPins);
        showSpectraForAllPinsButton.setEnabled(hasPins);
        showAveragePinSpectrumButton.setEnabled(hasPins); // todo - hasSpectraGraphs
        diagramCanvas.setEnabled(hasProduct);    // todo - hasSpectraGraphs
    }

    public void updateSpectra(int pixelX, int pixelY) {
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        SpectraDiagram spectraDiagram = getSpectraDiagram();
        if (spectraDiagram.getBands().length > 0) {
            spectraDiagram.updateSpectra(pixelX, pixelY);
            diagramCanvas.repaint();
        } else {
            diagramCanvas.setMessageText(MSG_NO_SPECTRAL_BANDS);
        }
    }

    private Band[] getSelectedSpectralBands() {
        return getSpectraDiagram().getBands();
    }

    private Band[] getAvailableSpectralBands() {
        Debug.assertNotNull(getCurrentProduct());
        Band[] bands = getCurrentProduct().getBands();
        ArrayList<Band> spectralBands = new ArrayList<Band>(15);
        for (Band band : bands) {
            if (band.getSpectralWavelength() > 0.0) {
                spectralBands.add(band);
            }
        }
        return spectralBands.toArray(new Band[spectralBands.size()]);
    }

    @Override
    public void componentOpened() {
        if (!tipShown) {
            VisatApp.getApp().showInfoDialog("Spectrum Tip",
                                             "Tip: If you press the SHIFT key while moving the mouse cursor over \n" +
                                                     "an image, VISAT adjusts the diagram axes to the local values at the\n" +
                                                     "current pixel position, if you release the SHIFT key again, then the\n" +
                                                     "min/max are accumulated again.", /*I18N*/
                                                                                       SUPPRESS_MESSAGE_KEY);
            tipShown = true;
        }
    }

    @Override
    public JComponent createControl() {
        filterButton = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Filter24.gif"), false);
        filterButton.setEnabled(false);
        filterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectSpectralBands();
            }
        });

        showSpectrumForCursorButton = ToolButtonFactory.createButton(new AbstractAction("showCursorSpectrum") {
            public void actionPerformed(ActionEvent e) {
                recreateSpectraDiagram();
            }
        }, true);
        showSpectrumForCursorButton.setSelected(true);
        showSpectrumForCursorButton.setText("C");
        showSpectrumForCursorButton.setToolTipText("Show spectrum at cursor position.");

        showSpectraForSelectedPinsButton = ToolButtonFactory.createButton(new AbstractAction("showSpectraForSelectedPins") {
            public void actionPerformed(ActionEvent e) {
                recreateSpectraDiagram();
            }
        }, true);
        showSpectrumForCursorButton.setSelected(true);
        showSpectraForSelectedPinsButton.setText("SP");
        showSpectraForSelectedPinsButton.setToolTipText("Show spectrum of selected pins.");

        showSpectraForAllPinsButton = ToolButtonFactory.createButton(new AbstractAction("showSpectraForAllPins") {
            public void actionPerformed(ActionEvent e) {
                recreateSpectraDiagram();
            }
        }, true);
        showSpectraForAllPinsButton.setText("AP");
        showSpectraForAllPinsButton.setToolTipText("Show spectra of all pins.");

        showAveragePinSpectrumButton = ToolButtonFactory.createButton(new AbstractAction("showAverageSpectrum") {
            public void actionPerformed(ActionEvent e) {
                // todo - implement
                JOptionPane.showMessageDialog(null, "Not implemented");
            }
        }, true);
        showAveragePinSpectrumButton.setText("Av");
        showAveragePinSpectrumButton.setToolTipText("Show average spectrum of all pin spectra.");

        AbstractButton exportSpectraButton = ToolButtonFactory.createButton(new SpectraExportAction(this), false);
        exportSpectraButton.setText("Ex");
        exportSpectraButton.setToolTipText("Export spectra to text file.");

        AbstractButton helpButton = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Help24.gif"), false);
        helpButton.setToolTipText("Help."); /*I18N*/

        ButtonGroup bg = new ButtonGroup();
        bg.add(showSpectraForSelectedPinsButton);
        bg.add(showSpectraForAllPinsButton);

        final JPanel buttonPane = GridBagUtils.createPanel();
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets.top = 2;
        gbc.gridy = 0;
        buttonPane.add(filterButton, gbc);
        gbc.gridy++;
        buttonPane.add(showSpectrumForCursorButton, gbc);
        gbc.gridy++;
        buttonPane.add(showSpectraForSelectedPinsButton, gbc);
        gbc.gridy++;
        buttonPane.add(showSpectraForAllPinsButton, gbc);
        gbc.gridy++;
        buttonPane.add(showAveragePinSpectrumButton, gbc);
        gbc.gridy++;
        buttonPane.add(exportSpectraButton, gbc);

        gbc.gridy++;
        gbc.insets.bottom = 0;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weighty = 1.0;
        gbc.gridwidth = 2;
        buttonPane.add(new JLabel(" "), gbc); // filler
        gbc.fill = GridBagConstraints.NONE;
        gbc.weighty = 0.0;
        gbc.gridy = 10;
        gbc.anchor = GridBagConstraints.EAST;
        buttonPane.add(helpButton, gbc);

        diagramCanvas = new DiagramCanvas();
        diagramCanvas.setPreferredSize(new Dimension(300, 200));
        diagramCanvas.setMessageText("No product selected."); /*I18N*/
        diagramCanvas.setBackground(Color.white);
        diagramCanvas.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        JPanel mainPane = new JPanel(new BorderLayout(4, 4));
        mainPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        mainPane.add(BorderLayout.CENTER, diagramCanvas);
        mainPane.add(BorderLayout.EAST, buttonPane);
        mainPane.setPreferredSize(new Dimension(320, 200));

        if (getDescriptor().getHelpId() != null) {
            HelpSys.enableHelpOnButton(helpButton, getDescriptor().getHelpId());
            HelpSys.enableHelpKey(mainPane, getDescriptor().getHelpId());
        }

        // Add an internal frame listsner to VISAT so that we can update our
        // spectrum dialog with the information of the currently activated
        // product scene view.
        //
        VisatApp.getApp().addInternalFrameListener(new SpectrumIFL());


        final ProductSceneView view = VisatApp.getApp().getSelectedProductSceneView();
        if (view != null) {
            handleViewActivated(view);
        } else {
            setCurrentView(view);
        }

        return mainPane;
    }

    private void selectSpectralBands() {
        Band[] allBandNames = getAvailableSpectralBands();
        Band[] selectedBands = getSelectedSpectralBands();
        if (selectedBands == null) {
            selectedBands = allBandNames;
        }
        BandChooser bandChooser = new BandChooser(getWindowAncestor(), "Available Spectral Bands",
                                                  getDescriptor().getHelpId(),
                                                  allBandNames, selectedBands);
        if (bandChooser.show() == ModalDialog.ID_OK) {
            getSpectraDiagram().setBands(bandChooser.getSelectedBands());
        }
    }

    public SpectraDiagram getSpectraDiagram() {
        Debug.assertNotNull(currentProduct);
        return productToDiagramMap.get(currentProduct);
    }

    private void setSpectraDiagram(final SpectraDiagram newDiagram) {
        Debug.assertNotNull(currentProduct);
        SpectraDiagram oldDiagram;
        if (newDiagram != null) {
            oldDiagram = productToDiagramMap.put(currentProduct, newDiagram);
        } else {
            oldDiagram = productToDiagramMap.remove(currentProduct);
        }
        if (oldDiagram != null && oldDiagram != newDiagram) {
            oldDiagram.dispose();
        }
    }

    public void resetAxesMinMaxAccumulators() {
        getSpectraDiagram().resetMinMaxAccumulators();
        getContentPane().repaint();
    }

    private CursorSpectrumPPL getOrCreateCursorSpectrumPPL(Product product) {
        CursorSpectrumPPL ppl = getCursorSpectrumPPL(product);
        if (ppl == null) {
            ppl = new CursorSpectrumPPL(product);
            cursorSpectrumPPLMap.put(product, ppl);
        }
        return ppl;
    }

    private CursorSpectrumPPL getCursorSpectrumPPL(Product product) {
        return cursorSpectrumPPLMap.get(product);
    }

    private boolean isVisible() {
        return getControl().isVisible();
    }

    private boolean isShowingCursorSpectrum() {
        return showSpectrumForCursorButton.isSelected();
    }

    private boolean isShowingPinSpectra() {
        return isShowingSpectraForSelectedPinsButton() || isShowingSpectraForAllPinsButton();
    }

    private boolean isShowingSpectraForAllPinsButton() {
        return showSpectraForAllPinsButton.isSelected();
    }


    private void recreateSpectraDiagram() {
        SpectraDiagram spectraDiagram = getSpectraDiagram();
        if (spectraDiagram != null) {
            spectraDiagram.dispose();
        }

        spectraDiagram = new SpectraDiagram(getCurrentProduct());

        if (isShowingCursorSpectrum()) {
            addSpectrumGraph(spectraDiagram, null);
        }

        if (isShowingSpectraForSelectedPinsButton()) {
            Pin[] pins = getCurrentProduct().getPins();
            for (Pin pin : pins) {
                if (pin.isSelected()) {
                    addSpectrumGraph(spectraDiagram, pin);
                }
            }
        } else if (isShowingSpectraForAllPinsButton()) {
            Pin[] pins = getCurrentProduct().getPins();
            for (Pin pin : pins) {
                addSpectrumGraph(spectraDiagram, pin);
            }
        }

        spectraDiagram.setBands(getAvailableSpectralBands());
        spectraDiagram.resetMinMaxAccumulators();
        spectraDiagram.updateYUnit();
        spectraDiagram.updateSpectra(pixelX, pixelY);
        setSpectraDiagram(spectraDiagram);
        diagramCanvas.setDiagram(spectraDiagram);
    }

    private static void addSpectrumGraph(SpectraDiagram spectraDiagram, Pin pin) {
        SpectrumGraph spectrumGraph = new SpectrumGraph(pin, spectraDiagram.getBands());
        DefaultDiagramGraphStyle style = (DefaultDiagramGraphStyle) spectrumGraph.getStyle();
        if (pin != null) {
            Paint fillPaint = pin.getSymbol().getFillPaint();
            if (fillPaint instanceof Color) {
                style.setOutlineColor(((Color) fillPaint).darker());
            } else {
                style.setOutlineColor(pin.getSymbol().getOutlineColor());
            }
            style.setOutlineStroke(pin.getSymbol().getOutlineStroke());
            style.setFillPaint(fillPaint);
        } else {
            style.setOutlineColor(Color.BLACK);
            style.setOutlineStroke(new BasicStroke(1.5f));
            style.setFillPaint(Color.WHITE);
        }
        spectraDiagram.addGraph(spectrumGraph);
    }

    private boolean isShowingSpectraForSelectedPinsButton() {
        return showSpectraForSelectedPinsButton.isSelected();
    }

    private void handleViewActivated(final ProductSceneView view) {
        final Product product = view.getProduct();
        view.addPixelPositionListener(getOrCreateCursorSpectrumPPL(product));
        setCurrentView(view);
    }

    private void handleViewDeactivated(final ProductSceneView view) {
        final Product product = view.getProduct();
        view.removePixelPositionListener(getCursorSpectrumPPL(product));
        setCurrentView(null);
    }

    /////////////////////////////////////////////////////////////////////////
    // View change handling

    private class SpectrumIFL extends InternalFrameAdapter {

        @Override
        public void internalFrameActivated(InternalFrameEvent e) {
            final Container contentPane = e.getInternalFrame().getContentPane();
            if (contentPane instanceof ProductSceneView) {
                handleViewActivated((ProductSceneView) contentPane);
            }
        }

        @Override
        public void internalFrameDeactivated(InternalFrameEvent e) {
            final Container contentPane = e.getInternalFrame().getContentPane();
            if (contentPane instanceof ProductSceneView) {
                handleViewDeactivated((ProductSceneView) contentPane);
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Pixel position change handling

    private class CursorSpectrumPPL implements PixelPositionListener {

        private final Product _product;

        public CursorSpectrumPPL(Product product) {
            _product = product;
        }

        public Product getProduct() {
            return _product;
        }

        public void pixelPosChanged(RenderedImage sourceImage,
                                    int pixelX,
                                    int pixelY,
                                    boolean pixelPosValid,
                                    MouseEvent e) {
            if (isActive()) {
                if (pixelPosValid) {
                    diagramCanvas.setMessageText(null);
                    updateSpectra(pixelX, pixelY);
                } else {
                    diagramCanvas.setMessageText("Pixel position invalid.");
                }
                if (e.isShiftDown()) {
                    resetAxesMinMaxAccumulators();
                }
            }
        }

        public void pixelPosNotAvailable(RenderedImage sourceImage) {
            if (isActive()) {
                diagramCanvas.setMessageText("Pixel position not available.");
            }
        }

        private boolean isActive() {
            return isVisible() && isShowingCursorSpectrum();
        }

    }

    /////////////////////////////////////////////////////////////////////////
    // Product change handling

    private class ProductNodeHandler extends ProductNodeListenerAdapter {
        @Override
        public void nodeChanged(final ProductNodeEvent event) {
            if (!isActive()) {
                return;
            }
            if (event.getSourceNode() instanceof Band) {
                final String propertyName = event.getPropertyName();
                if (propertyName.equals(DataNode.PROPERTY_NAME_UNIT)
                        || propertyName.equals(Band.PROPERTY_NAME_SPECTRAL_WAVELENGTH)) {
                    recreateSpectraDiagram();
                }
            } else if (event.getSourceNode() instanceof Pin) {
                if (isShowingPinSpectra()) {
                    recreateSpectraDiagram();
                }
            }
            updateUIState();
        }

        @Override
        public void nodeAdded(final ProductNodeEvent event) {
            if (!isActive()) {
                return;
            }
            if (event.getSourceNode() instanceof Band) {
                recreateSpectraDiagram();
            } else if (event.getSourceNode() instanceof Pin) {
                if (isShowingPinSpectra()) {
                    recreateSpectraDiagram();
                }
            }
            updateUIState();
        }

        @Override
        public void nodeRemoved(final ProductNodeEvent event) {
            if (!isActive()) {
                return;
            }
            if (event.getSourceNode() instanceof Band) {
                recreateSpectraDiagram();
            } else if (event.getSourceNode() instanceof Pin) {
                if (isShowingPinSpectra()) {
                    recreateSpectraDiagram();
                }
            }
            updateUIState();
        }

        private boolean isActive() {
            return isVisible() && getCurrentProduct() != null;
        }
    }

}
