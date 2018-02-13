/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.trends.databrowser3.ui.sampleview;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.csstudio.trends.databrowser3.Activator;
import org.csstudio.trends.databrowser3.Messages;
import org.csstudio.trends.databrowser3.model.Model;
import org.csstudio.trends.databrowser3.model.ModelItem;
import org.csstudio.trends.databrowser3.model.PlotSample;
import org.csstudio.trends.databrowser3.model.PlotSamples;
import org.phoebus.archive.vtype.VTypeHelper;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Panel for inspecting samples of a trace
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class SampleView extends VBox
{
    private final Model model;
    private final ComboBox<String> items = new ComboBox<>();
    private final TableView<PlotSample> sample_table = new TableView<>();
    private volatile String item_name = null;

    public SampleView(final Model model)
    {
        this.model = model;

        items.setOnAction(event -> select(items.getSelectionModel().getSelectedItem()));

        final Button refresh = new Button(Messages.SampleView_Refresh);
        refresh.setTooltip(new Tooltip(Messages.SampleView_RefreshTT));
        refresh.setOnAction(event -> update());

        final Label label = new Label(Messages.SampleView_Item);
        final HBox top_row = new HBox(5, label, items, refresh);
        top_row.setAlignment(Pos.CENTER_LEFT);

        // Combo should fill the available space.
        // Tried HBox.setHgrow(items, Priority.ALWAYS) etc.,
        // but always resulted in shrinking the label and button.
        // -> Explicitly compute combo width from available space
        //    minus padding and size of label, button
        items.prefWidthProperty().bind(top_row.widthProperty().subtract(20).subtract(label.widthProperty()).subtract(refresh.widthProperty()));
        items.prefHeightProperty().bind(refresh.heightProperty());

        createSampleTable();

        top_row.setPadding(new Insets(5));
        sample_table.setPadding(new Insets(0, 5, 5, 5));
        getChildren().setAll(top_row, sample_table);

        update();
    }

    private void createSampleTable()
    {
        // TODO All cell value factories. Color based on severity
        TableColumn<PlotSample, String> col = new TableColumn<>(Messages.TimeColumn);
        sample_table.getColumns().add(col);

        col = new TableColumn<>(Messages.ValueColumn);
        sample_table.getColumns().add(col);

        col = new TableColumn<>(Messages.SeverityColumn);
        col.setCellValueFactory(cell -> new SimpleStringProperty(VTypeHelper.getSeverity(cell.getValue().getVType()).name()));
        sample_table.getColumns().add(col);

        col = new TableColumn<>(Messages.StatusColumn);
        col.setCellValueFactory(cell -> new SimpleStringProperty(VTypeHelper.getMessage(cell.getValue().getVType())));
        sample_table.getColumns().add(col);

        col = new TableColumn<>(Messages.SampleView_Source);
        col.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSource()));
        sample_table.getColumns().add(col);

        sample_table.setMaxWidth(Double.MAX_VALUE);
        sample_table.setPlaceholder(new Label(Messages.SampleView_SelectItem));
    }

    private void select(final String item_name)
    {
        this.item_name = item_name;
        Activator.thread_pool.submit(this::getSamples);
    }

    private void update()
    {
        items.getItems().setAll( model.getItems().stream().map(item -> item.getName()).collect(Collectors.toList()) );
        if (item_name != null)
            items.getSelectionModel().select(item_name);
        // Update samples off the UI thread
        Activator.thread_pool.submit(this::getSamples);
    }

    private void getSamples()
    {
        final ModelItem item = model.getItem(item_name);
        final ObservableList<PlotSample> samples = FXCollections.observableArrayList();
        if (item != null)
        {
                final PlotSamples item_samples = item.getSamples();
                try
                {
                    if (item_samples.getLock().tryLock(2, TimeUnit.SECONDS))
                    {
                        final int N = item_samples.size();
                        for (int i=0; i<N; ++i)
                            samples.add(item_samples.get(i));
                        item_samples.getLock().unlock();
                    }
                }
                catch (Exception ex)
                {
                    Activator.logger.log(Level.WARNING, "Cannot access samples for " + item.getName(), ex);
                }
        }

        // Update UI
        Platform.runLater(() -> sample_table.setItems(samples));
    }
}
