/******************************************************************************
 * Copyright (c) 2015 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: JBoss by Red Hat - Initial implementation.
 *****************************************************************************/
package org.jboss.tools.fuse.transformation.editor.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jboss.tools.fuse.transformation.CustomMapping;
import org.jboss.tools.fuse.transformation.MappingOperation;
import org.jboss.tools.fuse.transformation.MappingType;
import org.jboss.tools.fuse.transformation.editor.Activator;
import org.jboss.tools.fuse.transformation.editor.function.Function;
import org.jboss.tools.fuse.transformation.editor.function.Function.Arg;
import org.jboss.tools.fuse.transformation.editor.internal.util.BaseDialog;
import org.jboss.tools.fuse.transformation.editor.internal.util.Util;
import org.jboss.tools.fuse.transformation.editor.internal.util.Util.Colors;
import org.jboss.tools.fuse.transformation.model.Model;

// TODO handle variable length args
class FunctionDialog extends BaseDialog {

    final MappingOperation<?, ?> mapping;
    final IProject project;
    Method origFunction, function;
    String[] argumentValues;

    ListViewer listViewer;
    Browser description;
    ScrolledComposite argScroller;
    TableViewer tableViewer;

    FunctionDialog(Shell shell,
                   MappingOperation<?, ?> mapping,
                   IProject project) {
        super(shell);
        this.mapping = mapping;
        this.project = project;
        if (mapping.getType() == MappingType.CUSTOM) {
            CustomMapping customMapping = (CustomMapping)mapping;
            try {
                Class<?> functionClass = Class.forName(customMapping.getFunctionClass());
                for (Method method : functionClass.getMethods()) {
                    if (method.getAnnotation(Function.class) != null && method.getName().equals(customMapping.getFunctionName()))
                        origFunction = method;
                }
            } catch (ClassNotFoundException e) {
                Activator.error(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.tools.fuse.transformation.editor.internal.util.BaseDialog#create()
     */
    @Override
    public void create() {
        super.create();
        // Select applicable method if editing a custom mapping
        if (origFunction != null) listViewer.setSelection(new StructuredSelection(origFunction));
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.tools.fuse.transformation.editor.internal.util.BaseDialog#constructContents(org.eclipse.swt.widgets.Composite)
     */
    @Override
    protected void constructContents(final Composite parent) {
        parent.setLayout(GridLayoutFactory.swtDefaults().numColumns(2).create());
        Group group = new Group(parent, SWT.SHADOW_ETCHED_OUT);
        group.setLayoutData(GridDataFactory.fillDefaults().grab(false, true).create());
        group.setLayout(GridLayoutFactory.swtDefaults().create());
        group.setText("Functions");
        listViewer = new ListViewer(group, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        listViewer.getList().setLayoutData(GridDataFactory.fillDefaults().grab(false, true).create());
        listViewer.setLabelProvider(new LabelProvider() {

            @Override
            public String getText(Object element) {
                return ((Method)element).getName();
            }
        });
        listViewer.setComparator(new ViewerComparator() {

            @Override
            public int compare(Viewer viewer,
                               Object element1,
                               Object element2) {
                return ((Method)element1).getName().compareTo(((Method)element2).getName());
            }
        });
        try {
            // Add all contributed functions
            String sourceType = ((Model)mapping.getSource()).getType();
            for (IConfigurationElement element : Platform.getExtensionRegistry().getConfigurationElementsFor(Activator.FUNCTION_EXTENSION_POINT)) {
                Object instance = element.createExecutableExtension("class");
                for (Method method : instance.getClass().getDeclaredMethods()) {
                    Class<?>[] types = method.getParameterTypes();
                    if (Modifier.isPublic(method.getModifiers())
                        && types.length > 0
                        && types[0].getName().equals(Util.nonPrimitiveClassName(sourceType)))
                        listViewer.add(method);
                }
            }
        } catch (Exception e) {
            Activator.error(e);
        }
        final SashForm splitter = new SashForm(parent, SWT.VERTICAL);
        splitter.setBackground(Colors.SASH);
        splitter.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        Composite pane = new Composite(splitter, SWT.NONE);
        pane.setLayout(GridLayoutFactory.fillDefaults().create());
        final Group descGroup = new Group(pane, SWT.NONE);
        descGroup.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        descGroup.setLayout(GridLayoutFactory.fillDefaults().create());
        descGroup.setText("Description");
        pane = new Composite(splitter, SWT.NONE);
        pane.setLayout(GridLayoutFactory.fillDefaults().create());
        final Group argsGroup = new Group(pane, SWT.NONE);
        argsGroup.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        argsGroup.setLayout(GridLayoutFactory.fillDefaults().create());
        argsGroup.setText("Arguments");
        listViewer.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                if (description != null) {
                    description.dispose();
                    description = null;
                }
                if (argScroller != null) {
                    argScroller.dispose();
                    argScroller = null;
                }

                if (event.getSelection().isEmpty()) {
                    function = null;
                    argumentValues = null;
                    getButton(IDialogConstants.OK_ID).setEnabled(false);
                    return;
                }

                function = (Method)((IStructuredSelection)event.getSelection()).getFirstElement();
                functionSelected(descGroup, argsGroup, parent);
            }
        });
    }

    private void functionSelected(Group descGroup,
                                  Group argsGroup,
                                  final Composite parent) {
        final Class<?>[] types = function.getParameterTypes();
        argumentValues = new String[types.length - 1];
        final Function annotation = function.getAnnotation(Function.class);

        description = new Browser(descGroup, SWT.BORDER);
        description.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        description.setText(annotation == null ? "" : annotation.description());

        if (types.length > 1) {
            argScroller = new ScrolledComposite(argsGroup, SWT.V_SCROLL);
            argScroller.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
            argScroller.setExpandHorizontal(true);
            argScroller.setExpandVertical(true);
            argScroller.setShowFocusedControl(true);
            final Composite argsPane = new Composite(argScroller, SWT.NONE);
            argScroller.setContent(argsPane);
            argsPane.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
            argsPane.setLayout(GridLayoutFactory.swtDefaults().numColumns(4).create());
            // Create new components for selected function's arguments
            Label label = new Label(argsPane, SWT.NONE);
            label.setLayoutData(GridDataFactory.fillDefaults().create());
            label.setText("Name");
            label.setBackground(label.getDisplay().getSystemColor(SWT.COLOR_BLACK));
            label.setForeground(label.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            label = new Label(argsPane, SWT.NONE);
            label.setLayoutData(GridDataFactory.fillDefaults().create());
            label.setText("Value");
            label.setBackground(label.getDisplay().getSystemColor(SWT.COLOR_BLACK));
            label.setForeground(label.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            label = new Label(argsPane, SWT.NONE);
            label.setLayoutData(GridDataFactory.fillDefaults().create());
            label.setText("Type");
            label.setBackground(label.getDisplay().getSystemColor(SWT.COLOR_BLACK));
            label.setForeground(label.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            label = new Label(argsPane, SWT.NONE);
            label.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
            label.setText("Description");
            label.setBackground(label.getDisplay().getSystemColor(SWT.COLOR_BLACK));
            label.setForeground(label.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            String[] mappingArgs = function.equals(origFunction) ? ((CustomMapping)mapping).getFunctionArguments() : null;
            for (int typeNdx = 1; typeNdx < types.length; typeNdx++) {
                final Class<?> type = types[typeNdx];
                final int argNdx = typeNdx - 1;
                final Arg argAnno =
                    annotation == null ? null : argNdx < annotation.args().length ? annotation.args()[argNdx] : null;
                if (argAnno != null) argumentValues[argNdx] = argAnno.defaultValue();
                label = new Label(argsPane, SWT.NONE);
                label.setLayoutData(GridDataFactory.swtDefaults().align(SWT.LEFT, SWT.TOP).create());
                if (argAnno == null) label.setText("argument" + (argNdx + 1));
                else label.setText(argAnno.name() + (argAnno.defaultValue().isEmpty() ? "" : " (optional)"));
                if (type == Boolean.class) {
                    final Button checkBox = new Button(argsPane, SWT.CHECK);
                    checkBox.setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.TOP).create());
                    if (mappingArgs != null) {
                        String val = mappingArgs[argNdx].split("=")[1];
                        argumentValues[argNdx] = val;
                        checkBox.setSelection(Boolean.valueOf(val));
                    }
                    checkBox.addSelectionListener(new SelectionAdapter() {

                        @Override
                        public void widgetSelected(SelectionEvent event) {
                            argumentValues[argNdx] = String.valueOf(checkBox.getSelection());
                            validate(annotation, types);
                        }
                    });
                } else {
                    final Text text = new Text(argsPane, SWT.BORDER);
                    text.setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.TOP).create());
                    if (mappingArgs != null) {
                        String val = mappingArgs[argNdx].split("=")[1];
                        argumentValues[argNdx] = val;
                        text.setText(val);
                    }
                    text.addModifyListener(new ModifyListener() {

                        @Override
                        public void modifyText(ModifyEvent event) {
                            String val = text.getText();
                            argumentValues[argNdx] = val.isEmpty() && argAnno != null ? argAnno.defaultValue() : val;
                            validate(annotation, types);
                        }
                    });
                }
                label = new Label(argsPane, SWT.NONE);
                label.setLayoutData(GridDataFactory.swtDefaults().align(SWT.LEFT, SWT.TOP).create());
                label.setText(type.getSimpleName());
                label.setToolTipText(type.getName());
                label = new Label(argsPane, SWT.WRAP);
                label.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
                if (argAnno != null) label.setText(argAnno.description());
            }
            argScroller.addControlListener(new ControlAdapter() {

                @Override
                public void controlResized(ControlEvent event) {
                    argScroller.setMinSize(argsPane.computeSize(argScroller.getClientArea().width, SWT.DEFAULT));
                    parent.layout();
                }
            });
            validate(annotation, types);
        } else getButton(IDialogConstants.OK_ID).setEnabled(true);
        descGroup.layout();
        argsGroup.layout();
    }

    private void validate(Function annotation,
                          Class<?>[] types) {
        boolean valid = true;
        for (int ndx = 0; ndx < argumentValues.length; ndx++) {
            final Arg arg = annotation == null ? null : ndx < annotation.args().length ? annotation.args()[ndx] : null;
            if (!Util.valid(argumentValues[ndx], arg, types[ndx + 1])) {
                valid = false;
                break;
            }
        }
        getButton(IDialogConstants.OK_ID).setEnabled(valid);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.tools.fuse.transformation.editor.internal.util.BaseDialog#message()
     */
    @Override
    protected String message() {
        return "Select a function to transform the " + ((Model)mapping.getSource()).getName()
               + " field's value, along with any applicable arguments";
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.tools.fuse.transformation.editor.internal.util.BaseDialog#title()
     */
    @Override
    protected String title() {
        return (origFunction == null ? "Add" : "Edit") + " Function";
    }
}