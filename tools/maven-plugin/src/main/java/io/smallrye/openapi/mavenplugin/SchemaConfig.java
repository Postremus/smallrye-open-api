package io.smallrye.openapi.mavenplugin;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SchemaConfig {
    /**
     * Filename of the schema
     * Default to openapi. So the files created will be openapi.yaml and openapi.json.
     */
    private String schemaFilename = "openapi";

    /**
     * When you include dependencies, we only look at compile and system scopes (by default)
     * You can change that here.
     * Valid options are: compile, provided, runtime, system, test, import
     */
    private List<String> includeDependenciesScopes = Arrays.asList("compile", "system");

    /**
     * When you include dependencies, we only look at jars (by default)
     * You can change that here.
     */
    private List<String> includeDependenciesTypes = Arrays.asList("jar");

    private File configProperties;

    // Properies as per OpenAPI Config.

    private String modelReader;

    private String filter;

    private Boolean scanDisabled;

    private String scanPackages;

    private String scanClasses;

    private String scanExcludePackages;

    private String scanExcludeClasses;

    private List<String> servers;

    private List<String> pathServers;

    private List<String> operationServers;

    private Boolean scanDependenciesDisable;

    private List<String> scanDependenciesJars;

    private Boolean schemaReferencesEnable;

    private String customSchemaRegistryClass;

    private Boolean applicationPathDisable;

    private String openApiVersion;

    private String infoTitle;

    private String infoVersion;

    private String infoDescription;

    private String infoTermsOfService;

    private String infoContactEmail;

    private String infoContactName;

    private String infoContactUrl;

    private String infoLicenseName;

    private String infoLicenseUrl;

    private String operationIdStrategy;

    public String getSchemaFilename() {
        return schemaFilename;
    }

    public void setSchemaFilename(String schemaFilename) {
        this.schemaFilename = schemaFilename;
    }

    public List<String> getIncludeDependenciesScopes() {
        return includeDependenciesScopes;
    }

    public void setIncludeDependenciesScopes(List<String> includeDependenciesScopes) {
        this.includeDependenciesScopes = includeDependenciesScopes;
    }

    public List<String> getIncludeDependenciesTypes() {
        return includeDependenciesTypes;
    }

    public void setIncludeDependenciesTypes(List<String> includeDependenciesTypes) {
        this.includeDependenciesTypes = includeDependenciesTypes;
    }

    public File getConfigProperties() {
        return configProperties;
    }

    public void setConfigProperties(File configProperties) {
        this.configProperties = configProperties;
    }

    public String getModelReader() {
        return modelReader;
    }

    public void setModelReader(String modelReader) {
        this.modelReader = modelReader;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Boolean getScanDisabled() {
        return scanDisabled;
    }

    public void setScanDisabled(Boolean scanDisabled) {
        this.scanDisabled = scanDisabled;
    }

    public String getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(String scanPackages) {
        this.scanPackages = scanPackages;
    }

    public String getScanClasses() {
        return scanClasses;
    }

    public void setScanClasses(String scanClasses) {
        this.scanClasses = scanClasses;
    }

    public String getScanExcludePackages() {
        return scanExcludePackages;
    }

    public void setScanExcludePackages(String scanExcludePackages) {
        this.scanExcludePackages = scanExcludePackages;
    }

    public String getScanExcludeClasses() {
        return scanExcludeClasses;
    }

    public void setScanExcludeClasses(String scanExcludeClasses) {
        this.scanExcludeClasses = scanExcludeClasses;
    }

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public List<String> getPathServers() {
        return pathServers;
    }

    public void setPathServers(List<String> pathServers) {
        this.pathServers = pathServers;
    }

    public List<String> getOperationServers() {
        return operationServers;
    }

    public void setOperationServers(List<String> operationServers) {
        this.operationServers = operationServers;
    }

    public Boolean getScanDependenciesDisable() {
        return scanDependenciesDisable;
    }

    public void setScanDependenciesDisable(Boolean scanDependenciesDisable) {
        this.scanDependenciesDisable = scanDependenciesDisable;
    }

    public List<String> getScanDependenciesJars() {
        return scanDependenciesJars;
    }

    public void setScanDependenciesJars(List<String> scanDependenciesJars) {
        this.scanDependenciesJars = scanDependenciesJars;
    }

    public Boolean getSchemaReferencesEnable() {
        return schemaReferencesEnable;
    }

    public void setSchemaReferencesEnable(Boolean schemaReferencesEnable) {
        this.schemaReferencesEnable = schemaReferencesEnable;
    }

    public String getCustomSchemaRegistryClass() {
        return customSchemaRegistryClass;
    }

    public void setCustomSchemaRegistryClass(String customSchemaRegistryClass) {
        this.customSchemaRegistryClass = customSchemaRegistryClass;
    }

    public Boolean getApplicationPathDisable() {
        return applicationPathDisable;
    }

    public void setApplicationPathDisable(Boolean applicationPathDisable) {
        this.applicationPathDisable = applicationPathDisable;
    }

    public String getOpenApiVersion() {
        return openApiVersion;
    }

    public void setOpenApiVersion(String openApiVersion) {
        this.openApiVersion = openApiVersion;
    }

    public String getInfoTitle() {
        return infoTitle;
    }

    public void setInfoTitle(String infoTitle) {
        this.infoTitle = infoTitle;
    }

    public String getInfoVersion() {
        return infoVersion;
    }

    public void setInfoVersion(String infoVersion) {
        this.infoVersion = infoVersion;
    }

    public String getInfoDescription() {
        return infoDescription;
    }

    public void setInfoDescription(String infoDescription) {
        this.infoDescription = infoDescription;
    }

    public String getInfoTermsOfService() {
        return infoTermsOfService;
    }

    public void setInfoTermsOfService(String infoTermsOfService) {
        this.infoTermsOfService = infoTermsOfService;
    }

    public String getInfoContactEmail() {
        return infoContactEmail;
    }

    public void setInfoContactEmail(String infoContactEmail) {
        this.infoContactEmail = infoContactEmail;
    }

    public String getInfoContactName() {
        return infoContactName;
    }

    public void setInfoContactName(String infoContactName) {
        this.infoContactName = infoContactName;
    }

    public String getInfoContactUrl() {
        return infoContactUrl;
    }

    public void setInfoContactUrl(String infoContactUrl) {
        this.infoContactUrl = infoContactUrl;
    }

    public String getInfoLicenseName() {
        return infoLicenseName;
    }

    public void setInfoLicenseName(String infoLicenseName) {
        this.infoLicenseName = infoLicenseName;
    }

    public String getInfoLicenseUrl() {
        return infoLicenseUrl;
    }

    public void setInfoLicenseUrl(String infoLicenseUrl) {
        this.infoLicenseUrl = infoLicenseUrl;
    }

    public String getOperationIdStrategy() {
        return operationIdStrategy;
    }

    public void setOperationIdStrategy(String operationIdStrategy) {
        this.operationIdStrategy = operationIdStrategy;
    }
}
