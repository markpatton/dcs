/*
 * Copyright 2014 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.gui;

import java.io.File;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.dataconservancy.packaging.gui.presenter.PackageDescriptionPresenter;
import org.dataconservancy.packaging.gui.presenter.Presenter;
import org.dataconservancy.packaging.gui.util.PackageToolPopup;
import org.dataconservancy.packaging.tool.model.PackageDescription;

/**
 * Root container for application that manages changes between presenters.
 */
public class Controller {
    private BorderPane container;
    private Factory factory;
    private Page currentPage;
    private PackageDescription packageDescription;
    private File packageDescriptionFile;
    private File contentRoot;
    private File rootArtifactDir;
    private File outputDirectory;
    private String packageGenerationParams;
    private String packageFilenameIllegalCharacters;
    private String buildNumber;
    private String buildRevision;
    private String buildTimeStamp;
    private PackageToolPopup crossPageProgressIndicatorPopUp;
    private Stack<Page> previousPages;
    
    /* For handling file dialog mutex locks as a MacOS bug workaround DC-1624 */
    private final ConcurrentHashMap<Object, Semaphore> locks = new ConcurrentHashMap<>();
    
    public Controller() {
        this.container = new BorderPane();
        container.getStyleClass().add(CssConstants.ROOT_CLASS);
        previousPages = new Stack<>();
    }

    public Factory getFactory() { return factory; }
    public void setFactory(Factory factory) { this.factory = factory; }

    public void startApp() {
        packageGenerationParams = factory.getConfiguration().getPackageGenerationParamsFile();
        packageFilenameIllegalCharacters = factory.getConfiguration().getPackageFilenameIllegalCharacters();
        showHome(true);
    }
    
    /**
     * Switch to home.
     * @param clear Set to true if the fields on the home page should be cleared, false if not.
     */
    public void showHome(boolean clear) {
        container.setTop((VBox)factory.getHeaderView());
        currentPage = Page.CREATE_NEW_PACKAGE;
        packageDescription = null;
        packageDescriptionFile = null;
        contentRoot = null;
        rootArtifactDir = null;

        if (clear) {
            clearPresenters();
        }
        showPage();
    }

    /**
     * Method to clear stale information from the presenters.
     */
    private void clearPresenters() {
        factory.getCreateNewPackagePresenter().clear();
        factory.getContentDirectoryPresenter().clear();
        factory.getPackageDescriptionPresenter().clear();
        factory.getPackageGenerationPresenter().clear();
    }

    /**
     * Switch to creating package description.
     */
    public void showCreatePackageDescription() {
        show(factory.getCreateNewPackagePresenter());
    }

    /**
     * Switch to the screen for selecting a content directory.
     */
    public void showSelectContentDirectory() {
        show(factory.getContentDirectoryPresenter());
    }

    /**
     * @return container node of controller
     */
    public Parent asParent() {
        return container;
    }

    /**
     * Shows the presenter and optionally clears the information
     * @param presenter The presenter to show
     */
    private void show(Presenter presenter) {
        container.setCenter(presenter.display());
    }
    
    public void showGeneratePackage() {
        show(factory.getPackageGenerationPresenter());
    }

    public PackageDescriptionPresenter showPackageDescriptionViewer() {
        PackageDescriptionPresenter presenter = factory.getPackageDescriptionPresenter();
        show(presenter);
        return presenter;
    }
    /**
     * Pops up a dialog that waits for the user to choose a file.
     * 
     * @param chooser the FileChooser
     * @return file chosen or null on cancel
     */
    public File showOpenFileDialog(FileChooser chooser) {
        Semaphore lock = getLock(chooser);
        
        /* We manually assure only one dialog box is open at a time, due to a MacOS bug DC-1624 */
        if (lock.tryAcquire()) {
            try {
                return chooser.showOpenDialog(factory.getStage());
            } finally {
                locks.remove(lock);
                lock.release();
            }
        } else {
            System.out.println("");
            return null;
        }
    }
    
    /** Pops up a save file dialog.
     * 
     * @param chooser  the FileChooser
     * @return File to save or null on cancel.
     */
    public File showSaveFileDialog(FileChooser chooser) {
        Semaphore lock = getLock(chooser);
        
        /* We manually assure only one dialog box is open at a time, due to a MacOS bug DC-1624 */
        if (lock.tryAcquire()) {
            try {
                return chooser.showSaveDialog(factory.getStage());
            } finally {
                locks.remove(lock);
                lock.release();
            }
        } else {
            return null;
        }
    }

    /**
     * Pops up a dialog that waits for the user to choose a directory
     * @param chooser the DirectoryChooser
     * @return directory chosen or null on cancel
     */
    public File showOpenDirectoryDialog(DirectoryChooser chooser) {
        Semaphore lock = getLock(chooser);

        /* We manually assure only one dialog box is open at a time, due to a MacOS bug DC-1624 */
        if (lock.tryAcquire()) {
            try {
                return chooser.showDialog(factory.getStage());
            } finally {
                locks.remove(lock);
                lock.release();
            }
        } else {
            return null;
        }
    }
    
    private Semaphore getLock(Object exclusive) {
        locks.putIfAbsent(exclusive, new Semaphore(1));
        return locks.get(exclusive);
    }
    
    /** 
     * A simple enumeration that is used to control flow in the application. There is an entry for each page in the application.
     * Each page contains it's order in the application as well as a title. 
     */
    //TODO: This enum is getting a bit unwieldy as enum at this point, and may need to be converted to a separate class -BMB
    public enum Page {

        //Positions must be in numerical order of there appearance in the workflow but don't need to be sequential
        //Space is left between pages to allow for the future addition of more screens
        CREATE_NEW_PACKAGE(10, Labels.LabelKey.CREATE_PACKAGE_PAGE),
        SELECT_CONTENT_DIRECTORY(11, Labels.LabelKey.CREATE_PACKAGE_PAGE),
        DEFINE_RELATIONSHIPS(20, Labels.LabelKey.DEFINE_RELATIONSHIPS_PAGE),
        GENERATE_PACKAGE(30, Labels.LabelKey.GENERATE_PACKAGE_PAGE);
        
        private int position;
        private Labels.LabelKey labelKey;
        
        Page(int position, Labels.LabelKey label) {
            this.position = position;
            this.labelKey = label;
        }
        
        /**
         * Returns the position of the page in the application.
         * @return  the position of the page
         */
        public int getPosition() {
            return position;
        }

        /**
         * Static method to get the page that corresponds to a specific position.
         * Note: This method is required because the pages are not zero indexed, or sequential so you can't simply access or loop through to find the correct page based on position.
         * @param position The position to retrieve the page for.
         * @return The page corresponding to the given position or null if none exist.
         */
        public static Page getPageByPosition(int position) {
            switch (position) {
                case 10:
                    return CREATE_NEW_PACKAGE;
                case 11:
                    return SELECT_CONTENT_DIRECTORY;
                case 20:
                    return DEFINE_RELATIONSHIPS;
                case 30:
                    return GENERATE_PACKAGE;
            }

            return null;
        }
        /**
         * Returns the label key to get the title of the page.
         * @return  the label key to get the title of the page
         */
        public Labels.LabelKey getLabelKey() {
            return labelKey;
        }

        /**
         * Determines if the page is valid to be shown, this is for conditional pages such as the the SELECT_CONTENT_DIRECTORY page.
         * For most pages this method will default to true since they're always valid to be shown.
         * @param controller The controller instance that's checking for page validity
         * @return True if the page should be shown, false if not
         */
        //TODO: I was hoping to make this a parameter of the enum but getting that to work proved to be beyond my java foo. So this method exists instead. -BMB
        public boolean isValidPage(Controller controller) {
            switch(this) {
                case SELECT_CONTENT_DIRECTORY:
                    if (controller.getPackageDescriptionFile() == null) {
                        return false;
                    }
                    break;
            }

            return true;
        }
    }
    
    //Advances the application to the next page. Or redisplays the current page if it's the last page.
    public void goToNextPage() {
        Page nextPage = currentPage;
        int currentPosition = currentPage.getPosition();
        int nextPosition = Integer.MAX_VALUE;
        for (Page pages : Page.values()) {
            if (pages.position > currentPosition && pages.position < nextPosition && pages.isValidPage(this)) {
                nextPosition = pages.position;
            }
        }
        
        if (nextPosition < Integer.MAX_VALUE) {
            Page pageForPosition = Page.getPageByPosition(nextPosition);
            if (pageForPosition != null) {
                nextPage = pageForPosition;
            }
        }

        previousPages.push(currentPage);
        currentPage = nextPage;
        showPage();
    }
    
    //Returns the application to the previous page, or redisplays the current page if it's the first page. 
    public void goToPreviousPage() {
        if (previousPages != null && !previousPages.isEmpty()) {
            currentPage = previousPages.pop();
            showPage();
        }
    }
    
    /**
     * Shows the current page, a tells the presenter if it should clear it's information. 
     */
    private void showPage() {
        factory.getHeaderView().highlightNextPage(currentPage.getPosition());
        switch (currentPage) {
            case CREATE_NEW_PACKAGE:
                showCreatePackageDescription();
                break;
            case DEFINE_RELATIONSHIPS:
                showPackageDescriptionViewer();
                break;
            case GENERATE_PACKAGE:
                showGeneratePackage();
                break;
            case SELECT_CONTENT_DIRECTORY:
                showSelectContentDirectory();
                break;
            default:
                //There is no next page do nothing
                break;
        }
    }
    
    public void setPackageDescription(PackageDescription description) {
        this.packageDescription = description;
    }
    
    public PackageDescription getPackageDescription() {
        return packageDescription;
    }
    
    public void setPackageDescriptionFile(File packageDescriptionFile){
        this.packageDescriptionFile = packageDescriptionFile;
    }

    public File getPackageDescriptionFile(){
        return packageDescriptionFile;
    }

    public File getContentRoot() {
        return contentRoot;
    }

    public void setContentRoot(File contentRoot) {
        this.contentRoot = contentRoot;
    }

    public File getRootArtifactDir() { return rootArtifactDir; }

    public void setRootArtifactDir(File rootArtifactDir) { this.rootArtifactDir = rootArtifactDir; }

    public File getOutputDirectory() { return outputDirectory; }

    public void setOutputDirectory(File outputDirectory) { this.outputDirectory = outputDirectory; }
    
    public String getPackageGenerationParamsFilePath() {
        return packageGenerationParams;
    }

    public String getPackageFilenameIllegalCharacters() { return packageFilenameIllegalCharacters; }

    public void setPackageFilenameIllegalCharacters(String illegalCharacters) { this.packageFilenameIllegalCharacters = illegalCharacters;}
    
    public void setBuildNumber(String buildNumberString) {
        this.buildNumber = buildNumberString;
    }
    
    public String getBuildNumber() {
        return buildNumber;
    }
    
    public void setBuildRevision(String buildRevision) {
        this.buildRevision = buildRevision;
    }
    
    public String getBuildRevision() {
        return buildRevision;
    }
    
    public void setBuildTimeStamp(String timeStamp) {
        this.buildTimeStamp = timeStamp;
    }
    
    public String getBuildTimeStamp() {
        return buildTimeStamp;
    }

    public PackageToolPopup getCrossPageProgressIndicatorPopUp() {
        return crossPageProgressIndicatorPopUp;
    }

    public void setCrossPageProgressIndicatorPopUp(PackageToolPopup crossPageProgressIndicatorPopUp) {
        this.crossPageProgressIndicatorPopUp = crossPageProgressIndicatorPopUp;
    }
}
