/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.git.client.checkout;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.git.shared.CheckoutRequest;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.git.GitServiceClient;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.git.client.GitLocalizationConstant;
import org.eclipse.che.ide.ext.git.client.outputconsole.GitOutputConsole;
import org.eclipse.che.ide.ext.git.client.outputconsole.GitOutputConsoleFactory;
import org.eclipse.che.ide.extension.machine.client.processes.panel.ProcessesPanelPresenter;

import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.FLOAT_MODE;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;

/**
 * Presenter for checkout reference(branch, tag) name or commit hash.
 *
 * @author Roman Nikitenko
 * @author Vlad Zhukovskyi
 */
@Singleton
public class CheckoutReferencePresenter implements CheckoutReferenceView.ActionDelegate {
    public static final String CHECKOUT_COMMAND_NAME = "Git checkout";

    private final NotificationManager     notificationManager;
    private final GitServiceClient        service;
    private final AppContext              appContext;
    private final GitLocalizationConstant constant;
    private final CheckoutReferenceView   view;
    private final DtoFactory              dtoFactory;
    private final GitOutputConsoleFactory gitOutputConsoleFactory;
    private final ProcessesPanelPresenter consolesPanelPresenter;

    private Project project;

    @Inject
    public CheckoutReferencePresenter(CheckoutReferenceView view,
                                      GitServiceClient service,
                                      AppContext appContext,
                                      GitLocalizationConstant constant,
                                      NotificationManager notificationManager,
                                      GitOutputConsoleFactory gitOutputConsoleFactory,
                                      ProcessesPanelPresenter consolesPanelPresenter,
                                      DtoFactory dtoFactory) {
        this.view = view;
        this.dtoFactory = dtoFactory;
        this.view.setDelegate(this);
        this.service = service;
        this.appContext = appContext;
        this.constant = constant;
        this.notificationManager = notificationManager;
        this.gitOutputConsoleFactory = gitOutputConsoleFactory;
        this.consolesPanelPresenter = consolesPanelPresenter;
    }

    /** Show dialog. */
    public void showDialog(Project project) {
        this.project = project;
        view.setCheckoutButEnableState(false);
        view.showDialog();
    }

    @Override
    public void onCancelClicked() {
        view.close();
    }

    @Override
    public void onCheckoutClicked(final String reference) {

        service.checkout(appContext.getDevMachine(), project.getLocation(), dtoFactory.createDto(CheckoutRequest.class).withName(reference))
               .then(new Operation<Void>() {
                   @Override
                   public void apply(Void arg) throws OperationException {
                       project.synchronize().then(new Operation<Resource[]>() {
                           @Override
                           public void apply(Resource[] arg) throws OperationException {
                               view.close();
                           }
                       });
                   }
               })
               .catchError(new Operation<PromiseError>() {
                   @Override
                   public void apply(PromiseError error) throws OperationException {
                       final String errorMessage = (error.getMessage() != null)
                                                   ? error.getMessage()
                                                   : constant.checkoutFailed();
                       GitOutputConsole console = gitOutputConsoleFactory.create(CHECKOUT_COMMAND_NAME);
                       console.printError(errorMessage);
                       consolesPanelPresenter.addCommandOutput(appContext.getDevMachine().getId(), console);
                       notificationManager.notify(constant.checkoutFailed(), FAIL, FLOAT_MODE);
                       view.close();
                   }
               });
    }

    @Override
    public void referenceValueChanged(String reference) {
        view.setCheckoutButEnableState(isInputCorrect(reference));
    }

    @Override
    public void onEnterClicked() {
        String reference = view.getReference();
        if (isInputCorrect(reference)) {
            onCheckoutClicked(reference);
        }
    }

    private boolean isInputCorrect(String reference) {
        return reference != null && !reference.isEmpty();
    }
}
