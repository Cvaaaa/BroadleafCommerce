/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
 * %%
 * Copyright (C) 2009 - 2023 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
package org.broadleafcommerce.openadmin.web.controller.entity;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.common.extension.ExtensionResultHolder;
import org.broadleafcommerce.common.extension.ExtensionResultStatusType;
import org.broadleafcommerce.common.persistence.EntityDuplicator;
import org.broadleafcommerce.common.presentation.client.AddMethodType;
import org.broadleafcommerce.common.presentation.client.SupportedFieldType;
import org.broadleafcommerce.common.sandbox.SandBoxHelper;
import org.broadleafcommerce.common.service.GenericEntityService;
import org.broadleafcommerce.common.util.BLCArrayUtils;
import org.broadleafcommerce.common.util.BLCMessageUtils;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.common.web.JsonResponse;
import org.broadleafcommerce.openadmin.dto.AdornedTargetCollectionMetadata;
import org.broadleafcommerce.openadmin.dto.AdornedTargetList;
import org.broadleafcommerce.openadmin.dto.BasicCollectionMetadata;
import org.broadleafcommerce.openadmin.dto.BasicFieldMetadata;
import org.broadleafcommerce.openadmin.dto.ClassMetadata;
import org.broadleafcommerce.openadmin.dto.ClassTree;
import org.broadleafcommerce.openadmin.dto.CollectionMetadata;
import org.broadleafcommerce.openadmin.dto.DynamicResultSet;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.dto.FieldMetadata;
import org.broadleafcommerce.openadmin.dto.FilterAndSortCriteria;
import org.broadleafcommerce.openadmin.dto.MapMetadata;
import org.broadleafcommerce.openadmin.dto.Property;
import org.broadleafcommerce.openadmin.dto.SectionCrumb;
import org.broadleafcommerce.openadmin.dto.TabMetadata;
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao;
import org.broadleafcommerce.openadmin.server.domain.PersistencePackageRequest;
import org.broadleafcommerce.openadmin.server.security.dao.AdminUserDao;
import org.broadleafcommerce.openadmin.server.security.domain.AdminSection;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser;
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType;
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService;
import org.broadleafcommerce.openadmin.server.service.persistence.PersistenceResponse;
import org.broadleafcommerce.openadmin.server.service.persistence.extension.AdornedTargetAutoPopulateExtensionManager;
import org.broadleafcommerce.openadmin.server.service.persistence.module.BasicPersistenceModule;
import org.broadleafcommerce.openadmin.web.controller.AdminAbstractController;
import org.broadleafcommerce.openadmin.web.controller.modal.ModalHeaderType;
import org.broadleafcommerce.openadmin.web.dao.MultipleCatalogExtensionManager;
import org.broadleafcommerce.openadmin.web.editor.NonNullBooleanEditor;
import org.broadleafcommerce.openadmin.web.form.component.DefaultListGridActions;
import org.broadleafcommerce.openadmin.web.form.component.ListGrid;
import org.broadleafcommerce.openadmin.web.form.entity.DefaultAdornedEntityFormActions;
import org.broadleafcommerce.openadmin.web.form.entity.DefaultEntityFormActions;
import org.broadleafcommerce.openadmin.web.form.entity.DefaultMainActions;
import org.broadleafcommerce.openadmin.web.form.entity.EntityForm;
import org.broadleafcommerce.openadmin.web.form.entity.EntityFormAction;
import org.broadleafcommerce.openadmin.web.form.entity.Field;
import org.broadleafcommerce.openadmin.web.form.entity.FieldGroup;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UrlPathHelper;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The default implementation of the {@link AdminAbstractController}. This delegates every call to 
 * super and does not provide any custom-tailored functionality. It is responsible for rendering the
 * admin for every entity that is not explicitly customized by its own controller.
 *
 * @author Andre Azzolini (apazzolini)
 * @author Jeff Fischer
 */
@Controller("blAdminBasicEntityController")
@RequestMapping("/{sectionKey:.+}")
public class AdminBasicEntityController extends AdminAbstractController {

    protected static final Log LOG = LogFactory.getLog(AdminBasicEntityController.class);

    public static final String ALTERNATE_ID_PROPERTY = "ALTERNATE_ID";
    public static final String CUSTOM_CRITERIA = "criteria";
    public static final String IS_SELECTIZE_REQUEST = "isSelectizeRequest";
    protected static final String CURRENT_FOLDER_ID = "currentFolderId";

    @Resource(name="blSandBoxHelper")
    protected SandBoxHelper sandBoxHelper;

    @Resource(name = "blAdminUserDao")
    protected AdminUserDao adminUserDao;

    @Resource(name="blDynamicEntityDao")
    protected DynamicEntityDao dynamicEntityDao;

    @Resource(name = "blAdornedTargetAutoPopulateExtensionManager")
    protected AdornedTargetAutoPopulateExtensionManager adornedTargetAutoPopulateExtensionManager;

    @Resource(name = "blRowLevelSecurityService")
    protected RowLevelSecurityService rowLevelSecurityService;
    
    @Resource(name = "blEntityDuplicator")
    protected EntityDuplicator duplicator;
    
    @Resource(name = "blGenericEntityService")
    protected GenericEntityService genericEntityService;

    @Resource(name = "blMultipleCatalogExtensionManager")
    protected MultipleCatalogExtensionManager multipleCatalogExtensionManager;

    // ******************************************
    // REQUEST-MAPPING BOUND CONTROLLER METHODS *
    // ******************************************

    /**
     * Renders the main entity listing for the specified class, which is based on the current sectionKey with some optional
     * criteria.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param requestParams a Map of property name -> list critiera values
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "", method = RequestMethod.GET)
    public String viewEntityList(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @RequestParam MultiValueMap<String, String> requestParams) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> crumbs = getSectionCrumbs(request, null, null);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, requestParams, crumbs, pathVars);
        ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        DynamicResultSet drs =  service.getRecords(ppr).getDynamicResultSet();

        ListGrid listGrid = formService.buildMainListGrid(drs, cmd, sectionKey, crumbs);
        listGrid.setSelectType(ListGrid.SelectType.NONE);

        Set<Field> headerFields = listGrid.getHeaderFields();
        if (CollectionUtils.isNotEmpty(headerFields)) {
            Field firstField = headerFields.iterator().next();
            if (requestParams.containsKey(firstField.getName())) {
                model.addAttribute("mainSearchTerm", requestParams.get(firstField.getName()).get(0));
            }
        }

        model.addAttribute("viewType", "entityList");

        setupViewEntityListBasicModel(request, cmd, sectionKey, sectionClassName, model, requestParams);
        model.addAttribute("listGrid", listGrid);

        return DEFAULT_CONTAINER_VIEW;
    }

    protected void setupViewEntityListBasicModel(HttpServletRequest request, ClassMetadata cmd, String sectionKey,
            String sectionClassName, Model model, MultiValueMap<String, String> requestParams) {
        List<EntityFormAction> mainActions = new ArrayList<>();
        addAddActionIfAllowed(sectionClassName, cmd, mainActions);
        extensionManager.getProxy().addAdditionalMainActions(sectionClassName, mainActions);
        extensionManager.getProxy().modifyMainActions(cmd, mainActions);

        // If this came from a delete save, we'll have a headerFlash request parameter to take care of
        if (requestParams.containsKey("headerFlash")) {
            model.addAttribute("headerFlash", requestParams.get("headerFlash").get(0));
        }

        List<ClassTree> entityTypes = getAddEntityTypes(cmd.getPolymorphicEntities());
        String requestUri = request.getRequestURI();
        if (!request.getContextPath().equals("/") && requestUri.startsWith(request.getContextPath())) {
            requestUri = requestUri.substring(request.getContextPath().length() + 1, requestUri.length());
        }

        model.addAttribute("isFilter", (requestParams.size() > 0));
        model.addAttribute("currentUri", requestUri);
        model.addAttribute("entityTypes", entityTypes);
        model.addAttribute("entityFriendlyName", cmd.getPolymorphicEntities().getFriendlyName());
        model.addAttribute("currentUrl", request.getRequestURL().toString());
        model.addAttribute("mainActions", mainActions);
        setModelAttributes(model, sectionKey);
        setTypedEntityModelAttributes(request, model);
    }

    @RequestMapping(value = "/selectize", method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> viewEntityListSelectize(HttpServletRequest request,
             HttpServletResponse response, Model model,
             @PathVariable Map<String, String> pathVars,
             @RequestParam MultiValueMap<String, String> requestParams) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> crumbs = getSectionCrumbs(request, null, null);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, requestParams, crumbs, pathVars)
                .withCustomCriteria(getCustomCriteria(requestParams));

        ppr.addCustomCriteria(buildSelectizeCustomCriteria());

        ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        DynamicResultSet drs =  service.getRecords(ppr).getDynamicResultSet();

        return formService.constructSelectizeOptionMap(drs, cmd);
    }

    /**
     * Obtains the requested criteria parameter
     *
     * @param requestParams
     * @return
     */
    protected String[] getCustomCriteria(Map<String, List<String>> requestParams) {
        if (requestParams == null || requestParams.isEmpty()) {
            return null;
        }

        List<String> criteria = requestParams.get(CUSTOM_CRITERIA);
        String response = CollectionUtils.isEmpty(criteria) ? null : criteria.get(0);
        return new String[] {response};
    }

    /**
     * Adds the "Add" button to the main entity form if the current user has permissions to create new instances
     * of the entity and all of the fields in the entity aren't marked as read only.
     *
     * @param sectionClassName
     * @param cmd
     * @param mainActions
     */
    protected void addAddActionIfAllowed(String sectionClassName, ClassMetadata cmd, List<EntityFormAction> mainActions) {
        if (isAddActionAllowed(sectionClassName, cmd)) {
            mainActions.add(DefaultMainActions.ADD);
        }
    }

    protected boolean isAddActionAllowed(final String sectionClassName, final ClassMetadata cmd) {
        // If the user does not have create permissions, we will not add the "Add New" button
        try {
            adminRemoteSecurityService.securityCheck(sectionClassName, EntityOperationType.ADD);
        } catch (ServiceException e) {
            if (e instanceof SecurityServiceException) {
                return false;
            }
        }

        final boolean canAdd = rowLevelSecurityService
                .canAdd(adminRemoteSecurityService.getPersistentAdminUser(), sectionClassName, cmd);
        return isNotReadOnly(cmd) && canAdd;
    }
    
    protected boolean isNotReadOnly(final ClassMetadata cmd) {
        //check if all the metadata is read only
        for (Property property : cmd.getProperties()) {
            if (property.getMetadata() instanceof BasicFieldMetadata) {
                if (((BasicFieldMetadata) property.getMetadata()).getReadOnly() == null ||
                        !((BasicFieldMetadata) property.getMetadata()).getReadOnly()) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Renders the modal form that is used to add a new parent level entity. Note that this form cannot render any
     * subcollections as operations on those collections require the parent level entity to first be saved and have
     * and id. Once the entity is initially saved, we will redirect the user to the normal manage entity screen where
     * they can then perform operations on sub collections.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param entityType
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/add", method = RequestMethod.GET)
    public String viewAddEntityForm(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @RequestParam(defaultValue = "") String entityType) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, null, null);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, sectionCrumbs, pathVars);
        ppr.setAddOperationInspect(true);
        ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        
        entityType = determineEntityType(entityType, cmd);

        EntityForm entityForm = formService.createEntityForm(cmd, sectionCrumbs);

        // We need to make sure that the ceiling entity is set to the interface and the specific entity type
        // is set to the type we're going to be creating.
        entityForm.setCeilingEntityClassname(cmd.getCeilingType());
        entityForm.setEntityType(entityType);

        // When we initially build the class metadata (and thus, the entity form), we had all of the possible
        // polymorphic fields built out. Now that we have a concrete entity type to render, we can remove the
        // fields that are not applicable for this given entity type.
        formService.removeNonApplicableFields(cmd, entityForm, entityType);

        modifyAddEntityForm(entityForm, pathVars);

        model.addAttribute("entityForm", entityForm);
        model.addAttribute("viewType", "modal/entityAdd");

        model.addAttribute("entityFriendlyName", cmd.getPolymorphicEntities().getFriendlyName());
        model.addAttribute("currentUrl", request.getRequestURL().toString());
        model.addAttribute("modalHeaderType", ModalHeaderType.ADD_ENTITY.getType());
        setModelAttributes(model, sectionKey);
        return MODAL_CONTAINER_VIEW;
    }

    // If the entity type isn't specified, we need to determine if there are various polymorphic 
    // types for this entity.
    protected String determineEntityType(String entityType, final ClassMetadata cmd) 
            throws UnsupportedEncodingException {
        if (StringUtils.isBlank(entityType)) {
            if (cmd.getPolymorphicEntities().getChildren().length == 0) {
                entityType = cmd.getPolymorphicEntities().getFullyQualifiedClassname();
            } else {
                entityType = getDefaultEntityType();
            }
        } else {
            entityType = URLDecoder.decode(entityType, "UTF-8");
        }
        return entityType;
    }

    /**
     * Processes the request to add a new entity. If successful, returns a redirect to the newly created entity.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param entityForm
     * @param result
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public String addEntity(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @ModelAttribute(value="entityForm") EntityForm entityForm, BindingResult result) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, null, null);
        ClassMetadata cmd = service.getClassMetadata(getSectionPersistencePackageRequest(sectionClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();

        extractDynamicFormFields(cmd, entityForm);
        String[] sectionCriteria = customCriteriaService.mergeSectionCustomCriteria(sectionClassName, getSectionCustomCriteria());
        Entity entity = service.addEntity(entityForm, sectionCriteria, sectionCrumbs).getEntity();
        entityFormValidator.validate(entityForm, entity, result);

        if (result.hasErrors()) {
            entityForm.clearFieldsMap();
            formService.populateEntityForm(cmd, entity, entityForm, sectionCrumbs);

            formService.removeNonApplicableFields(cmd, entityForm, entityForm.getEntityType());

            modifyAddEntityForm(entityForm, pathVars);

            model.addAttribute("viewType", "modal/entityAdd");
            model.addAttribute("currentUrl", request.getRequestURL().toString());
            model.addAttribute("modalHeaderType", ModalHeaderType.ADD_ENTITY.getType());
            model.addAttribute("entityFriendlyName", cmd.getPolymorphicEntities().getFriendlyName());
            setModelAttributes(model, sectionKey);
            return MODAL_CONTAINER_VIEW;
        }

        // Note that AJAX Redirects need the context path prepended to them
        return "ajaxredirect:" + getContextPath(request) + sectionKey + "/" + entity.getPMap().get("id").getValue();
    }

    @RequestMapping(value = "/{id}/duplicate", method = RequestMethod.POST)
    public String duplicateEntity(final HttpServletRequest request,
            final HttpServletResponse response, 
            final Model model,
            @PathVariable final Map<String, String> pathVars,
            @PathVariable(value = "id") final String id,
            @ModelAttribute(value = "entityForm") final EntityForm entityForm,
            final BindingResult result) throws Exception {
        final String sectionKey = getSectionKey(pathVars);
        final String sectionClassName = getClassNameForSection(sectionKey);
        final Class<?> entityClass = dynamicEntityDao.getImplClass(sectionClassName);
        final long identifier = Long.parseLong(id);
        
        if (duplicator.validate(entityClass, identifier)) {
            final Object duplicate;
            
            try {
                duplicate = duplicator.copy(entityClass, identifier);
            } catch (Exception e) {
                LOG.error("Could not duplicate entity", e);
                return getErrorDuplicatingResponse(response, "Duplication_Failure");
            }

            final Serializable dupId = genericEntityService.getIdentifier(duplicate);
            
            // Note that AJAX Redirects need the context path prepended to them
            return "ajaxredirect:" + getContextPath(request) + sectionKey + "/" + dupId;
        } else {
            return getErrorDuplicatingResponse(response, "Validation_Failure");
        }
    }

    protected String getErrorDuplicatingResponse(HttpServletResponse response, String code) {
        List<Map<String, Object>> errors = new ArrayList<>();
        String message;
        BroadleafRequestContext context = BroadleafRequestContext.getBroadleafRequestContext();
        if (context != null && context.getMessageSource() != null) {
            message = context.getMessageSource()
                    .getMessage(code, null, code, context.getJavaLocale());
        } else {
            LOG.warn(
                    "Could not find the MessageSource on the current request, " 
                            + "not translating the message key");
            message = "Duplication_Failure";
        }

        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("errorType", "global");
        errorMap.put("code", code);
        errorMap.put("message", message);
        errors.add(errorMap);
        return new JsonResponse(response).with("errors", errors).done();
    }

    /**
     * Renders the main entity form for the specified entity
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String viewEntityForm(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable("id") String id) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> crumbs = getSectionCrumbs(request, sectionKey, id);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, crumbs, pathVars);

        ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        Entity entity = service.getRecord(ppr, id, cmd, false).getDynamicResultSet().getRecords()[0];

        multipleCatalogExtensionManager.getProxy().setCurrentCatalog(entity, model);

        Map<String, DynamicResultSet> subRecordsMap = getViewSubRecords(request, pathVars, cmd, entity, crumbs);

        EntityForm entityForm = formService.createEntityForm(cmd, entity, subRecordsMap, crumbs);

        modifyEntityForm(entity, entityForm, pathVars);

        if (request.getParameter("headerFlash") != null) {
            model.addAttribute("headerFlash", request.getParameter("headerFlash"));
        }

        // Set the sectionKey again incase this is a typed entity
        entityForm.setSectionKey(sectionKey);

        // Build the current url in the cast that this is a typed entity
        String originatingUri = new UrlPathHelper().getOriginatingRequestUri(request);
        int startIndex = request.getContextPath().length();

        // Remove the context path from servlet path
        String currentUrl = originatingUri.substring(startIndex);

        model.addAttribute("entity", entity);
        model.addAttribute("entityForm", entityForm);
        model.addAttribute("currentUrl", currentUrl);
        model.addAttribute(CURRENT_FOLDER_ID, getCurrentFolderId(request));

        setModelAttributes(model, sectionKey);

        // We want to replace author ids with their names
        addAuditableDisplayFields(entityForm);

        return resolveAppropriateEntityView(request, model, entityForm);
    }

    protected Map<String, DynamicResultSet> getViewSubRecords(HttpServletRequest request, Map<String, String> pathVars,
                                                              ClassMetadata cmd, Entity entity,
                                                              List<SectionCrumb> crumbs) throws Exception {
        String tabName = pathVars.get("tabName");
        if (StringUtils.isEmpty(tabName)) {
            tabName = cmd.getFirstTab() == null ? "General" : cmd.getFirstTab().getTabName();
        }
        return service.getRecordsForSelectedTab(cmd, entity, crumbs, tabName);
    }

    private boolean isAddRequest(Entity entity) {
        ExtensionResultHolder<Boolean> resultHolder = new ExtensionResultHolder<>();
        ExtensionResultStatusType result = extensionManager.getProxy().isAddRequest(entity, resultHolder);
        if (result.equals(ExtensionResultStatusType.NOT_HANDLED)) {
            return false;
        }

        return resultHolder.getResult();
    }

    /**
     * Attempts to get the List Grid for the selected tab.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param tabName
     * @param entityForm
     * @param entity
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{tab:[0-9]+}/{tabName}", method = RequestMethod.POST)
    public String viewEntityTab(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value = "id") String id,
            @PathVariable(value = "tabName") String tabName,
            @ModelAttribute(value = "entityForm") EntityForm entityForm,
            @ModelAttribute(value = "entity") Entity entity) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> crumbs = getSectionCrumbs(request, sectionKey, id);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, crumbs, pathVars);
        ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        entity = service.getRecord(ppr, id, cmd, false).getDynamicResultSet().getRecords()[0];
        Map<String, DynamicResultSet> subRecordsMap = getViewSubRecords(request, pathVars, cmd, entity, crumbs);
        entityForm = formService.createEntityForm(cmd, entity, subRecordsMap, crumbs);

        modifyEntityForm(entity, entityForm, pathVars);

        model.addAttribute("entity", entity);
        model.addAttribute("entityForm", entityForm);
        model.addAttribute("currentUrl", request.getRequestURL().toString());

        setModelAttributes(model, sectionKey);

        model.addAttribute("useAjaxUpdate", true);
        model.addAttribute("viewType", "entityEdit");
        
        return DEFAULT_CONTAINER_VIEW;
    }

    /**
     * Builds JSON that looks like this:
     *
     * {"errors":
     *      [{"message":"This field is Required",
     *        "code": "requiredValidationFailure"
     *        "field":"defaultSku--name",
     *        "errorType", "field",
     *        "tab": "General"
     *        },
     *        {"message":"This field is Required",
     *        "code": "requiredValidationFailure"
     *        "field":"defaultSku--name",
     *        "errorType", "field",
     *        "tab": "General"
     *        }]
     * }
     *
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.POST, produces = "application/json")
    public String saveEntityJson(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value = "id") String id,
            @ModelAttribute(value = "entityForm") EntityForm entityForm, BindingResult result,
            RedirectAttributes ra) throws Exception {

        saveEntity(request, response, model, pathVars, id, entityForm, result, ra);

        JsonResponse json = new JsonResponse(response);
        if (result.hasErrors()) {
            populateJsonValidationErrors(entityForm, result, json);
        }
        List<String> dirtyList = buildDirtyList(pathVars, request, id);
        if (CollectionUtils.isNotEmpty(dirtyList)) {
            json.with("dirty", dirtyList);
        }

        ExtensionResultHolder<String> resultHolder = new ExtensionResultHolder<>();
        ExtensionResultStatusType resultStatusType = extensionManager.getProxy().overrideSaveEntityJsonResponse(response, result.hasErrors(), getSectionKey(pathVars), id, resultHolder);
        if (resultStatusType.equals(ExtensionResultStatusType.HANDLED)) {
            return resultHolder.getResult();
        }

        return json.done();
    }

    public List<String> buildDirtyList(Map<String, String> pathVars, HttpServletRequest request, String id) throws ServiceException {
        List<String> dirtyList = new ArrayList<>();
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, sectionCrumbs, pathVars);
        ClassMetadata cmd = null;
        Entity entity = null;
        cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        entity = service.getRecord(ppr, id, cmd, false).getDynamicResultSet().getRecords()[0];

        for (Property p: entity.getProperties()) {
            if (p.getIsDirty()) {
                dirtyList.add(p.getName());
            }
        }
        return dirtyList;
    }

    /**
     * Attempts to save the given entity. If validation is unsuccessful, it will re-render the entity form with
     * error fields highlighted. On a successful save, it will refresh the entity page.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param entityForm
     * @param result
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.POST)
    public String saveEntity(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @ModelAttribute(value="entityForm") EntityForm entityForm, BindingResult result,
            RedirectAttributes ra) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, sectionCrumbs, pathVars);
        ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();

        extractDynamicFormFields(cmd, entityForm);

        String[] sectionCriteria = customCriteriaService.mergeSectionCustomCriteria(sectionClassName, getSectionCustomCriteria());
        Entity entity = service.updateEntity(entityForm, sectionCriteria, sectionCrumbs).getEntity();

        entityFormValidator.validate(entityForm, entity, result);
        
        if (result.hasErrors()) {
            model.addAttribute("headerFlash", "save.unsuccessful");
            model.addAttribute("headerFlashAlert", true);

            Map<String, DynamicResultSet> subRecordsMap = service.getRecordsForAllSubCollections(ppr, entity, sectionCrumbs);
            entityForm.clearFieldsMap();
            formService.populateEntityForm(cmd, entity, subRecordsMap, entityForm, sectionCrumbs);

            modifyEntityForm(entity, entityForm, pathVars);

            model.addAttribute("entity", entity);
            model.addAttribute("currentUrl", request.getRequestURL().toString());

            setModelAttributes(model, sectionKey);

            return resolveAppropriateEntityView(request, model, entityForm);
        }

        ra.addFlashAttribute("headerFlash", "save.successful");

        return "redirect:/" + sectionKey + "/" + id;
    }
    
    protected void modifyEntityForm(final Entity entity, final EntityForm entityForm, 
            final Map<String, String> pathVars) throws Exception {
        if (isAddRequest(entity)) {
            modifyAddEntityForm(entityForm, pathVars);
        } else {
            modifyEntityForm(entityForm, pathVars);
        }
    }

    protected String resolveAppropriateEntityView(final HttpServletRequest request,
            final Model model,
            final @ModelAttribute(value = "entityForm") EntityForm entityForm) {
        if (isAjaxRequest(request)) {
            entityForm.setReadOnly();
            model.addAttribute("viewType", "modal/entityView");
            model.addAttribute("modalHeaderType", ModalHeaderType.VIEW_ENTITY.getType());
            return MODAL_CONTAINER_VIEW;
        } else {
            model.addAttribute("useAjaxUpdate", true);
            model.addAttribute("viewType", "entityEdit");
            return DEFAULT_CONTAINER_VIEW;
        }
    }

    /**
     * Attempts to remove the given entity.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/delete", method = RequestMethod.POST)
    public String removeEntity(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @ModelAttribute(value="entityForm") EntityForm entityForm, BindingResult result,
            RedirectAttributes ra) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);

        String[] sectionCriteria = customCriteriaService.mergeSectionCustomCriteria(sectionClassName, getSectionCustomCriteria());
        Entity entity = service.removeEntity(entityForm, sectionCriteria, sectionCrumbs).getEntity();
        // Removal does not normally return an Entity unless there is some validation error
        if (entity != null) {
            entityFormValidator.validate(entityForm, entity, result);
            if (result.hasErrors()) {
                // Create a flash attribute for the unsuccessful delete
                FlashMap fm = new FlashMap();
                fm.put("headerFlash", "delete.unsuccessful");
                fm.put("headerFlashAlert", true);
                request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, fm);

                // Re-look back up the entity so that we can return something populated
                PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, sectionCrumbs, pathVars);
                ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
                entity = service.getRecord(ppr, id, cmd, false).getDynamicResultSet().getRecords()[0];
                Map<String, DynamicResultSet> subRecordsMap = service.getRecordsForAllSubCollections(ppr, entity, sectionCrumbs);
                entityForm.clearFieldsMap();
                formService.populateEntityForm(cmd, entity, subRecordsMap, entityForm, sectionCrumbs);
                modifyEntityForm(entityForm, pathVars);

                return populateJsonValidationErrors(entityForm, result, new JsonResponse(response))
                        .done();
            }
        }

        ra.addFlashAttribute("headerFlash", "delete.successful");
        ra.addFlashAttribute("headerFlashAlert", true);

        if (isAjaxRequest(request)) {
            // redirect attributes won't work here since ajaxredirect actually makes a new request
            return "ajaxredirect:" + getContextPath(request) + sectionKey + "?headerFlash=delete.successful";
        } else {
            return "redirect:/" + sectionKey;
        }
    }

    @RequestMapping(value = "/{collectionField:.*}/details", method = RequestMethod.GET)
    public @ResponseBody Map<String, String> getCollectionValueDetails(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="collectionField") String collectionField,
            @RequestParam String ids,
            @RequestParam MultiValueMap<String, String> requestParams) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String sectionClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, null, null);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(sectionClassName, requestParams, sectionCrumbs, pathVars);
        ClassMetadata mainMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);
        FieldMetadata md = collectionProperty.getMetadata();

        ppr = PersistencePackageRequest.fromMetadata(md, sectionCrumbs);
        ppr.setStartIndex(getStartIndex(requestParams));
        ppr.setMaxIndex(getMaxIndex(requestParams));

        if (md instanceof BasicFieldMetadata) {
            String idProp = ((BasicFieldMetadata) md).getForeignKeyProperty();
            String displayProp = ((BasicFieldMetadata) md).getForeignKeyDisplayValueProperty();

            List<String> filterValues = BLCArrayUtils.asList(ids.split(FILTER_VALUE_SEPARATOR_REGEX));
            ppr.addFilterAndSortCriteria(new FilterAndSortCriteria(idProp, filterValues));

            DynamicResultSet drs = service.getRecords(ppr).getDynamicResultSet();
            Map<String, String> returnMap = new HashMap<>();

            for (Entity e : drs.getRecords()) {
                String id = e.getPMap().get(idProp).getValue();
                String disp = e.getPMap().get(displayProp).getDisplayValue();

                if (StringUtils.isBlank(disp)) {
                    disp = e.getPMap().get(displayProp).getValue();
                }

                returnMap.put(id,  disp);
            }

            return returnMap;
        }

        return null;
    }

    /**
     * Shows the modal popup for the current selected "to-one" field. For instance, if you are viewing a list of products
     * then this method is invoked when a user clicks on the name of the default category field.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param collectionField
     * @param id
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/{collectionField:.*}/{id}/view", method = RequestMethod.GET)
    public String viewCollectionItemDetails(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="id") String id) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);
        BasicFieldMetadata md = (BasicFieldMetadata) collectionProperty.getMetadata();

        AdminSection section = adminNavigationService.findAdminSectionByClassAndSectionId(md.getForeignKeyClass(), sectionKey);
        String sectionUrlKey = (section.getUrl().startsWith("/")) ? section.getUrl().substring(1) : section.getUrl();
        Map<String, String> varsForField = new HashMap<>();
        varsForField.put("sectionKey", sectionUrlKey);
        return viewEntityForm(request, response, model, varsForField, id);
    }

    @RequestMapping(
            value = "/{id}/{collectionField:.*}/{collectionItemId}/{tab:[0-9]+}/{tabName}",
            method = RequestMethod.POST
    )
    public String viewCollectionItemTab(HttpServletRequest request, HttpServletResponse response, Model model,
                                        @PathVariable  Map<String, String> pathVars,
                                        @PathVariable(value="id") String id,
                                        @PathVariable(value="collectionField") String collectionField,
                                        @PathVariable(value="collectionItemId") String collectionItemId,
                                        @PathVariable(value="tabName") String tabName,
                                        @ModelAttribute(value = "entityForm") EntityForm entityForm) throws Exception {

        return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, ModalHeaderType.VIEW_COLLECTION_ITEM.getType(), entityForm, null);
    }


    @RequestMapping(
            value = "/{id}/{collectionField:.*}/{collectionItemId}/view/{tab:[0-9]+}/{tabName}",
            method = RequestMethod.POST
    )
    public String viewReadOnlyCollectionItemTab(HttpServletRequest request, HttpServletResponse response, Model model,
                                        @PathVariable  Map<String, String> pathVars,
                                        @PathVariable(value="id") String id,
                                        @PathVariable(value="collectionField") String collectionField,
                                        @PathVariable(value="collectionItemId") String collectionItemId,
                                        @PathVariable(value="tabName") String tabName,
                                        @ModelAttribute(value = "entityForm") EntityForm entityForm) throws Exception {

        return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, ModalHeaderType.VIEW_COLLECTION_ITEM.getType(), entityForm, null);
    }


    /**
     * Returns the records for a given collectionField filtered by a particular criteria
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param collectionField
     * @param requestParams
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}", method = RequestMethod.GET)
    public String getCollectionFieldRecords(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @RequestParam  MultiValueMap<String, String> requestParams) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, requestParams, sectionCrumbs, pathVars);
        ClassMetadata mainMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);

        ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        Entity entity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];

        // Next, we must get the new list grid that represents this collection
        ListGrid listGrid = getCollectionListGrid(mainMetadata, entity, collectionProperty, requestParams, sectionKey, sectionCrumbs);
        model.addAttribute("listGrid", listGrid);

        model.addAttribute("currentParams", new ObjectMapper().writeValueAsString(requestParams));

        // We return the new list grid so that it can replace the currently visible one
        setModelAttributes(model, sectionKey);
        return "views/standaloneListGrid";
    }

    /**
     * Shows the modal dialog that is used to add an item to a given collection. There are several possible outcomes
     * of this call depending on the type of the specified collection field.
     *
     * <ul>
     *  <li>
     *    <b>Basic Collection (Persist)</b> - Renders a blank form for the specified target entity so that the user may
     *    enter information and associate the record with this collection. Used by fields such as ProductAttribute.
     *  </li>
     *  <li>
     *    <b>Basic Collection (Lookup)</b> - Renders a list grid that allows the user to click on an entity and select it.
     *    Used by fields such as "allParentCategories".
     *  </li>
     *  <li>
     *    <b>Adorned Collection (without form)</b> - Renders a list grid that allows the user to click on an entity and
     *    select it. The view rendered by this is identical to basic collection (lookup), but will perform the operation
     *    on an adorned field, which may carry extra meta-information about the created relationship, such as order.
     *  </li>
     *  <li>
     *    <b>Adorned Collection (with form)</b> - Renders a list grid that allows the user to click on an entity and
     *    select it. Once the user selects the entity, he will be presented with an empty form based on the specified
     *    "maintainedAdornedTargetFields" for this field. Used by fields such as "crossSellProducts", which in addition
     *    to linking an entity, provide extra fields, such as a promotional message.
     *  </li>
     *  <li>
     *    <b>Map Collection</b> - Renders a form for the target entity that has an additional key field. This field is
     *    populated either from the configured map keys, or as a result of a lookup in the case of a key based on another
     *    entity. Used by fields such as the mediaMap on a Sku.
     *  </li>
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param requestParams
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/add", method = RequestMethod.GET)
    public String showAddCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value = "id") String id,
            @PathVariable(value = "collectionField") String collectionField,
            @RequestParam MultiValueMap<String, String> requestParams) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName,
                sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);
        FieldMetadata md = collectionProperty.getMetadata();

        PersistencePackageRequest ppr = PersistencePackageRequest.fromMetadata(md, sectionCrumbs)
                .withFilterAndSortCriteria(getCriteria(requestParams))
                .withStartIndex(getStartIndex(requestParams))
                .withMaxIndex(getMaxIndex(requestParams))
                .withLastId(getLastId(requestParams))
                .withFirstId(getFirstId(requestParams))
                .withUpperCount(getUpperCount(requestParams))
                .withLowerCount(getLowerCount(requestParams))
                .withPageSize(getPageSize(requestParams))
                .withPresentationFetch(true);

        if (md instanceof BasicCollectionMetadata) {
            BasicCollectionMetadata fmd = (BasicCollectionMetadata) md;
            if (fmd.getAddMethodType().equals(AddMethodType.PERSIST)) {
                ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
                // If the entity type isn't specified, we need to determine if there are various polymorphic types
                // for this entity.
                String entityType = null;
                
                if (requestParams.containsKey("entityType")) {
                    entityType = requestParams.get("entityType").get(0);
                }
                
                entityType = determineEntityType(entityType, cmd);

                if (StringUtils.isBlank(entityType)) {
                    return getModalForBlankEntityType(request, model, sectionKey, cmd);
                } 
                
                ppr = ppr.withCeilingEntityClassname(entityType);
            }
        } else if (md instanceof MapMetadata) {
            ExtensionResultStatusType result = extensionManager.getProxy().modifyModelForAddCollectionType(request,response,model,sectionKey,id,requestParams,(MapMetadata) md);
            if (result.equals(ExtensionResultStatusType.HANDLED)) {
                model.addAttribute("entityId", id);
                model.addAttribute("sectionKey", sectionKey);
                model.addAttribute("collectionField", collectionField);
                return MODAL_CONTAINER_VIEW;
            }
        }

        model.addAttribute("currentParams", new ObjectMapper().writeValueAsString(requestParams));

        return buildAddCollectionItemModel(request, response, model, id, collectionField, sectionKey, collectionProperty, md, ppr, null, null);
    }
    
    protected String getModalForBlankEntityType(final HttpServletRequest request, 
            final Model model, final String sectionKey, final ClassMetadata cmd) {
        final List<ClassTree> entityTypes = getAddEntityTypes(cmd.getPolymorphicEntities());
        model.addAttribute("entityTypes", entityTypes);
        model.addAttribute("viewType", "modal/entityTypeSelection");
        model.addAttribute("entityFriendlyName", cmd.getPolymorphicEntities().getFriendlyName());
        
        String requestUri = request.getRequestURI();
        final String contextPath = request.getContextPath();
        
        if (!contextPath.equals("/") && requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length() + 1, requestUri.length());
        }

        model.addAttribute("currentUri", requestUri);
        model.addAttribute("modalHeaderType", ModalHeaderType.ADD_ENTITY.getType());
        setModelAttributes(model, sectionKey);
        
        return MODAL_CONTAINER_VIEW;
    }

    @RequestMapping(value = "/{id}/{collectionField:.*}/add/{collectionItemId}/verify", method = RequestMethod.POST)
    public @ResponseBody Map<String, Object> addCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);
        FieldMetadata md = collectionProperty.getMetadata();
        Map<String, Object> responseMap = new HashMap<>();
        if (md instanceof AdornedTargetCollectionMetadata) {
            adornedTargetAutoPopulateExtensionManager.getProxy().autoSetAdornedTargetManagedFields(md, mainClassName, id,
                    collectionField,
                    collectionItemId, responseMap);
        }
        return responseMap;
    }

    /**
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param requestParams
     * @return Json collection data
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/selectize", method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> getSelectizeCollectionOptions(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value = "id") String id,
            @PathVariable(value = "collectionField") String collectionField,
            @RequestParam MultiValueMap<String, String> requestParams) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName,
                sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);
        FieldMetadata md = collectionProperty.getMetadata();

        PersistencePackageRequest ppr = PersistencePackageRequest.fromMetadata(md, sectionCrumbs)
                .withFilterAndSortCriteria(getCriteria(requestParams))
                .withStartIndex(getStartIndex(requestParams))
                .withMaxIndex(getMaxIndex(requestParams));
        String[] both = Stream.concat(Arrays.stream(ppr.getCustomCriteria()), Arrays.stream(buildSelectizeCustomCriteria()))
                .toArray(String[]::new);
        ppr = ppr.withCustomCriteria( both);

        if (md instanceof AdornedTargetCollectionMetadata) {
            ppr.setOperationTypesOverride(null);
            ppr.setType(PersistencePackageRequest.Type.STANDARD);
            ppr.setSectionEntityField(collectionField);
        }

        DynamicResultSet drs = service.getRecords(ppr).getDynamicResultSet();

        return formService.buildSelectizeCollectionInfo(id, drs, collectionProperty, sectionKey, sectionCrumbs);
    }

    protected String[] buildSelectizeCustomCriteria() {
        return new String[]{ IS_SELECTIZE_REQUEST };
    }

    /**
     * Adds the requested collection item via Selectize
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param entityForm
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/selectize-add", method = RequestMethod.POST)
    public @ResponseBody Map<String, Object> addSelectizeCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @ModelAttribute(value="entityForm") EntityForm entityForm, BindingResult result) throws Exception {
        Map<String, Object> returnVal = new HashMap<>();
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);

        if (StringUtils.isBlank(entityForm.getEntityType())) {
            FieldMetadata fmd = collectionProperty.getMetadata();
            if (fmd instanceof BasicCollectionMetadata) {
                entityForm.setEntityType(((BasicCollectionMetadata) fmd).getCollectionCeilingEntity());
            }
        }

        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        ppr.addCustomCriteria(buildSelectizeCustomCriteria());
        declareShouldIgnoreAdditionStatusFilter();
        Entity entity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];

        // First, we must save the collection entity
        PersistenceResponse persistenceResponse = service.addSubCollectionEntity(entityForm, mainMetadata, collectionProperty, entity, sectionCrumbs);
        Entity savedEntity = persistenceResponse.getEntity();
        entityFormValidator.validate(entityForm, savedEntity, result);

        if (result.hasErrors()) {
            returnVal.put("error", result.getFieldError());
            return returnVal;
        }

        if (savedEntity.findProperty(ALTERNATE_ID_PROPERTY) != null) {
            returnVal.put("alternateId", savedEntity.findProperty(ALTERNATE_ID_PROPERTY).getValue());
        }
        return returnVal;
    }

    protected void declareShouldIgnoreAdditionStatusFilter() {
        Map<String, Object> additionalProperties = BroadleafRequestContext.getBroadleafRequestContext().getAdditionalProperties();
        additionalProperties.put("shouldIgnoreAdditionStatusFilter", true);
    }

    /**
     * Adds the requested collection item
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param entityForm
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/add", method = RequestMethod.POST)
    public String addCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @ModelAttribute(value="entityForm") EntityForm entityForm, BindingResult result) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);

        if (StringUtils.isBlank(entityForm.getEntityType())) {
            FieldMetadata fmd = collectionProperty.getMetadata();
            if (fmd instanceof BasicCollectionMetadata) {
                entityForm.setEntityType(((BasicCollectionMetadata) fmd).getCollectionCeilingEntity());
            }
        }

        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        declareShouldIgnoreAdditionStatusFilter();
        Entity entity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];
        service.clearEntityManager();

        // First, we must save the collection entity
        PersistenceResponse persistenceResponse = service.addSubCollectionEntity(entityForm, mainMetadata, collectionProperty, entity, sectionCrumbs);
        Entity savedEntity = persistenceResponse.getEntity();
        entityFormValidator.validate(entityForm, savedEntity, result);

        if (result.hasErrors()) {
            FieldMetadata md = collectionProperty.getMetadata();
            ppr = PersistencePackageRequest.fromMetadata(md, sectionCrumbs);
            return buildAddCollectionItemModel(request, response, model, id, collectionField, sectionKey, collectionProperty,
                    md, ppr, entityForm, savedEntity);
        }

        // Next, we must get the new list grid that represents this collection
        ListGrid listGrid = getCollectionListGrid(mainMetadata, entity, collectionProperty, null, sectionKey, persistenceResponse, sectionCrumbs);
        model.addAttribute("listGrid", listGrid);

        // We return the new list grid so that it can replace the currently visible one
        model.addAttribute("actualEntityId", id);
        setModelAttributes(model, sectionKey);
        return "views/standaloneListGrid";
    }

    @RequestMapping(value = "/{id}/{collectionField:.*}/addEmpty", method = RequestMethod.POST)
    public @ResponseBody String addEmptyCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @ModelAttribute(value="entityForm") EntityForm entityForm, BindingResult result) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);

        if (StringUtils.isBlank(entityForm.getEntityType())) {
            FieldMetadata fmd = collectionProperty.getMetadata();
            if (fmd instanceof BasicCollectionMetadata) {
                entityForm.setEntityType(((BasicCollectionMetadata) fmd).getCollectionCeilingEntity());
            }
        }

        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        Entity entity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];
        entity.setIsPreAdd(true);
        // First, we must save the collection entity
        PersistenceResponse persistenceResponse = service.addSubCollectionEntity(entityForm, mainMetadata, collectionProperty, entity, sectionCrumbs);
        Entity savedEntity = persistenceResponse.getEntity();

        return new JsonResponse(response)
                .with("status", "complete")
                .with("id", savedEntity.findProperty(entityForm.getIdProperty()).getValue())
                .done();
    }

    /**
     * Builds out all of the model information needed for showing the add modal for collection items on both the initial GET
     * as well as after a POST with validation errors
     *
     * @param request
     * @param model
     * @param id
     * @param collectionField
     * @param sectionKey
     * @param collectionProperty
     * @param md
     * @param ppr
     * @return the appropriate view to display for the modal
     * @see {@link #addCollectionItem(HttpServletRequest, HttpServletResponse, Model, Map, String, String, EntityForm, BindingResult)}
     * @see {@link #showAddCollectionItem(HttpServletRequest, HttpServletResponse, Model, Map, String, String, MultiValueMap)}
     * @throws ServiceException
     */
    protected String buildAddCollectionItemModel(HttpServletRequest request, HttpServletResponse response,
            Model model, String id, String collectionField, String sectionKey, Property collectionProperty,
            FieldMetadata md, PersistencePackageRequest ppr, EntityForm entityForm, Entity entity) throws ServiceException {

        // For requests to add a new collection item include the main class that the subsequent request comes from.
        // For instance, with basic collections we know the main associated class for a fetch through the ForeignKey
        // persistence item but map and adorned target lookups make a standard persistence request. This solution
        // fixes all cases.
        String mainClassName = getClassNameForSection(sectionKey);
        ppr.addCustomCriteria("owningClass=" + mainClassName);
        ppr.setAddOperationInspect(true);

        if (entityForm != null) {
            entityForm.clearFieldsMap();
        }
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        if (md instanceof BasicCollectionMetadata) {
            BasicCollectionMetadata fmd = (BasicCollectionMetadata) md;

            // When adding items to basic collections, we will sometimes show a form to persist a new record
            // and sometimes show a list grid to allow the user to associate an existing record.
            if (fmd.getAddMethodType().equals(AddMethodType.PERSIST)) {
                ClassMetadata collectionMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
                if (entityForm == null) {
                    entityForm = formService.createEntityForm(collectionMetadata,sectionCrumbs);
                    entityForm.setCeilingEntityClassname(ppr.getCeilingEntityClassname());
                    entityForm.setEntityType(ppr.getCeilingEntityClassname());
                } else {
                    formService.populateEntityForm(collectionMetadata, entityForm, sectionCrumbs);
                    formService.populateEntityFormFieldValues(collectionMetadata, entity, entityForm);
                }
                formService.removeNonApplicableFields(collectionMetadata, entityForm, ppr.getCeilingEntityClassname());
                entityForm.getTabs().iterator().next().getIsVisible();

                model.addAttribute("entityForm", entityForm);
                model.addAttribute("viewType", "modal/simpleAddEntity");
            } else {
                DynamicResultSet drs = service.getRecords(ppr).getDynamicResultSet();
                ListGrid listGrid = formService.buildCollectionListGrid(id, drs, collectionProperty, sectionKey, sectionCrumbs);
                listGrid.setPathOverride(request.getRequestURL().toString());

                if (AddMethodType.LOOKUP.equals(fmd.getAddMethodType()) || AddMethodType.SELECTIZE_LOOKUP.equals(fmd.getAddMethodType())) {
                    listGrid.removeAllRowActions();
                }

                model.addAttribute("listGrid", listGrid);
                model.addAttribute("viewType", "modal/simpleSelectEntity");
            }
        } else if (md instanceof AdornedTargetCollectionMetadata) {
            AdornedTargetCollectionMetadata fmd = (AdornedTargetCollectionMetadata) md;

            // Even though this field represents an adorned target collection, the list we want to show in the modal
            // is the standard list grid for the target entity of this field
            ppr.setOperationTypesOverride(null);
            ppr.setType(PersistencePackageRequest.Type.STANDARD);
            ppr.setSectionEntityField(collectionField);

            ClassMetadata collectionMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();

            DynamicResultSet drs = service.getRecords(ppr).getDynamicResultSet();
            ListGrid listGrid = formService.buildCollectionListGrid(id, drs, collectionProperty, sectionKey, sectionCrumbs);
            listGrid.setSubCollectionFieldName(collectionField);
            listGrid.setPathOverride(request.getRequestURL().toString());
            listGrid.setFriendlyName(collectionMetadata.getPolymorphicEntities().getFriendlyName());
            if (entityForm == null) {
                entityForm = formService.buildAdornedListForm(fmd, ppr.getAdornedList(), id, false, sectionCrumbs, true);
                entityForm.setCeilingEntityClassname(ppr.getAdornedList().getAdornedTargetEntityClassname());
            } else {
                formService.buildAdornedListForm(fmd, ppr.getAdornedList(), id, false, entityForm, sectionCrumbs, true);
                formService.populateEntityFormFieldValues(collectionMetadata, entity, entityForm);
            }

            listGrid.setListGridType(ListGrid.Type.ADORNED);
            for (Entry<String, Field> entry : entityForm.getFields().entrySet()) {
                if (entry.getValue().getIsVisible()) {
                    listGrid.setListGridType(ListGrid.Type.ADORNED_WITH_FORM);
                    break;
                }
            }

            // This is part of an add, so we want to be able to filter/sort the listgrid
            listGrid.setIsSortable(false);
            listGrid.setCanFilterAndSort(true);
            listGrid.removeAllRowActions();

            model.addAttribute("listGrid", listGrid);
            model.addAttribute("entityForm", entityForm);
            model.addAttribute("viewType", "modal/adornedSelectEntity");
        } else if (md instanceof MapMetadata) {
            MapMetadata fmd = (MapMetadata) md;
            ClassMetadata collectionMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();

            if (entityForm == null) {
                entityForm = formService.buildMapForm(fmd, ppr.getMapStructure(), collectionMetadata, id);
            } else {
                formService.buildMapForm(fmd, ppr.getMapStructure(), collectionMetadata, id, entityForm);
                formService.populateEntityFormFieldValues(collectionMetadata, entity, entityForm);
            }
            model.addAttribute("entityForm", entityForm);
            model.addAttribute("viewType", "modal/mapAddEntity");
        }

        // Set the parent id on the entity form
        if (entityForm != null) {
            entityForm.setParentId(id);
        }

        model.addAttribute("currentUrl", request.getRequestURL().toString());
        model.addAttribute("modalHeaderType", ModalHeaderType.ADD_COLLECTION_ITEM.getType());
        model.addAttribute("collectionProperty", collectionProperty);
        setModelAttributes(model, sectionKey);
        return MODAL_CONTAINER_VIEW;
    }

    /**
     * Shows the appropriate modal dialog to edit the selected collection item
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/{alternateId}", method = RequestMethod.GET)
    public String showUpdateCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId,
            @PathVariable(value="alternateId") String alternateId) throws Exception {
        return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, alternateId,
                ModalHeaderType.UPDATE_COLLECTION_ITEM.getType());
    }

    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}", method = RequestMethod.GET)
    public String showUpdateCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId) throws Exception {
        return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, null,
                ModalHeaderType.UPDATE_COLLECTION_ITEM.getType());
    }

    /**
     * Shows the appropriate modal dialog to view the selected collection item. This will display the modal as readonly
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/{alternateId}/view", method = RequestMethod.GET)
    public String showViewCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId,
            @PathVariable(value="alternateId") String alternateId) throws Exception {
        String returnPath = showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, alternateId,
                ModalHeaderType.VIEW_COLLECTION_ITEM.getType());

        // Since this is a read-only view, actions don't make sense in this context
        EntityForm ef = (EntityForm) model.asMap().get("entityForm");
        addAuditableDisplayFields(ef);
        ef.removeAllActions();
        ef.setReadOnly();

        return returnPath;
    }

    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/view", method = RequestMethod.GET)
    public String showViewCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId) throws Exception {
        String returnPath = showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, null,
                ModalHeaderType.VIEW_COLLECTION_ITEM.getType());

        // Since this is a read-only view, actions don't make sense in this context
        EntityForm ef = (EntityForm) model.asMap().get("entityForm");
        ef.removeAllActions();
        ef.setReadOnly();

        return returnPath;
    }

    protected String showViewUpdateCollection(HttpServletRequest request, Model model, Map<String, String> pathVars,
            String id, String collectionField, String collectionItemId, String alternateId, String modalHeaderType) throws ServiceException {
        return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, alternateId, modalHeaderType, null, null);
    }

    protected String showViewUpdateCollection(HttpServletRequest request, Model model, Map<String, String> pathVars,
            String id, String collectionField, String collectionItemId, String modalHeaderType) throws ServiceException {
        return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, null, modalHeaderType, null, null);
    }

    protected String showViewUpdateCollection(HttpServletRequest request, Model model, Map<String, String> pathVars,
                String id, String collectionField, String collectionItemId, String modalHeaderType, EntityForm entityForm, Entity entity) throws ServiceException {
        return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, null, modalHeaderType, entityForm, entity);
    }

    /**
     * Shows the view and populates the model for updating a collection item. You can also pass in an entityform and entity
     * which are optional. If they are not passed in then they are automatically looked up
     *
     * @param request
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId
     * @param modalHeaderType
     * @param entityForm
     * @param entity
     * @return
     * @throws ServiceException
     */
    protected String showViewUpdateCollection(HttpServletRequest request, 
            Model model, 
            Map<String, String> pathVars,
            String id, 
            String collectionField, 
            String collectionItemId, 
            String alternateId, 
            String modalHeaderType, 
            EntityForm entityForm, 
            Entity entity) throws ServiceException {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);
        FieldMetadata md = collectionProperty.getMetadata();
        SectionCrumb nextCrumb = new SectionCrumb();
        if (md instanceof MapMetadata) {
            nextCrumb.setSectionIdentifier(((MapMetadata) md).getValueClassName());
        } else {
            nextCrumb.setSectionIdentifier(((CollectionMetadata) md).getCollectionCeilingEntity());
        }
        nextCrumb.setSectionId(collectionItemId);
        if (!sectionCrumbs.contains(nextCrumb)) {
            sectionCrumbs.add(nextCrumb);
        }

        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        Entity parentEntity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];

        ppr = PersistencePackageRequest.fromMetadata(md, sectionCrumbs);

        if (md instanceof BasicCollectionMetadata &&
                (((BasicCollectionMetadata) md).getAddMethodType().equals(AddMethodType.PERSIST) ||
                        ((BasicCollectionMetadata) md).getAddMethodType().equals(AddMethodType.PERSIST_EMPTY))) {
            BasicCollectionMetadata fmd = (BasicCollectionMetadata) md;

            ClassMetadata collectionMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
            if (entity == null) {
                entity = service.getRecord(ppr, collectionItemId, collectionMetadata, true).getDynamicResultSet().getRecords()[0];
            }

            String currentTabName = getCurrentTabName(pathVars, collectionMetadata);
            Map<String, DynamicResultSet> subRecordsMap = service.getRecordsForSelectedTab(collectionMetadata, entity, sectionCrumbs, currentTabName);
            
            entityForm = reinitializeEntityForm(entityForm, collectionMetadata, entity, 
                    subRecordsMap, sectionCrumbs);
            
            entityForm.removeAction(DefaultEntityFormActions.DELETE);
            entityForm.removeAction(DefaultEntityFormActions.DUPLICATE);
            
            addAuditableDisplayFields(entityForm);
            model.addAttribute("entityForm", entityForm);
            model.addAttribute("viewType", "modal/simpleEditEntity");
        } else if (md instanceof AdornedTargetCollectionMetadata) {
            AdornedTargetCollectionMetadata fmd = (AdornedTargetCollectionMetadata) md;

            if (entity == null) {
                entity = service.getAdvancedCollectionRecord(mainMetadata, parentEntity, collectionProperty,
                    collectionItemId, sectionCrumbs, alternateId).getDynamicResultSet().getRecords()[0];
            }

            boolean populateTypeAndId = true;
            boolean isViewCollectionItem = ModalHeaderType.VIEW_COLLECTION_ITEM.getType().equals(modalHeaderType);
            if (entityForm == null) {
                entityForm = formService.buildAdornedListForm(fmd, ppr.getAdornedList(), id, isViewCollectionItem, sectionCrumbs, false);
                entityForm.removeAction(DefaultAdornedEntityFormActions.Add);
                entityForm.addAction(DefaultAdornedEntityFormActions.Save);
            } else {
                entityForm.clearFieldsMap();
                String entityType = entityForm.getEntityType();
                formService.buildAdornedListForm(fmd, ppr.getAdornedList(), id, isViewCollectionItem, entityForm, sectionCrumbs, false);
                entityForm.setEntityType(entityType);
                populateTypeAndId = false;
            }

            Map<String, Object> responseMap = new HashMap<>();
            adornedTargetAutoPopulateExtensionManager.getProxy().autoSetAdornedTargetManagedFields(md, mainClassName, id,
                    collectionField,
                    collectionItemId, responseMap);

            //For AdornedTargetCollections, we need to be specific on what translation ceilingEntity and Id is used.
            //It should be the entity and referenced Id of the adorned entity (not the target entity and Id).
            entityForm.setTranslationCeilingEntity(entityForm.getEntityType());
            entityForm.setTranslationId(alternateId);

            ClassMetadata cmd = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
            for (String field : fmd.getMaintainedAdornedTargetFields()) {
                if (responseMap.containsKey(field) && responseMap.containsKey("autoSubmit")) {
                    continue;
                }
                Property p = cmd.getPMap().get(field);
                if (p != null && p.getMetadata() instanceof AdornedTargetCollectionMetadata) {
                    // Because we're dealing with a nested adorned target collection, this particular request must act
                    // directly on the first adorned target collection. Because of this, we need the actual id property
                    // from the entity that models the adorned target relationship, and not the id of the target object.
                    Property alternateIdProperty = entity.getPMap().get(BasicPersistenceModule.ALTERNATE_ID_PROPERTY);
                    DynamicResultSet drs = service.getRecordsForCollection(cmd, entity, p, null, null, null,
                            alternateIdProperty.getValue(), sectionCrumbs).getDynamicResultSet();

                    ListGrid listGrid = formService.buildCollectionListGrid(alternateIdProperty.getValue(), drs, p,
                            ppr.getAdornedList().getAdornedTargetEntityClassname(), sectionCrumbs);
                    listGrid.getToolbarActions().add(DefaultListGridActions.ADD);

                    if (drs.getUnselectedTabMetadata().get(EntityForm.DEFAULT_TAB_NAME) != null) {
                        entityForm.addListGrid(cmd, listGrid, EntityForm.DEFAULT_TAB_NAME, EntityForm.DEFAULT_TAB_ORDER, fmd.getGroup(), true);
                    } else {
                        entityForm.addListGrid(cmd, listGrid, EntityForm.DEFAULT_TAB_NAME, EntityForm.DEFAULT_TAB_ORDER, fmd.getGroup(), false);
                    }
                } else if (p != null && p.getMetadata() instanceof MapMetadata) {
                    // See above comment for AdornedTargetCollectionMetadata
                    MapMetadata mmd = (MapMetadata) p.getMetadata();

                    Property alternateIdProperty = entity.getPMap().get(BasicPersistenceModule.ALTERNATE_ID_PROPERTY);
                    DynamicResultSet drs = service.getRecordsForCollection(cmd, entity, p, null, null, null,
                            alternateIdProperty.getValue(), sectionCrumbs).getDynamicResultSet();

                    ListGrid listGrid = formService.buildCollectionListGrid(alternateIdProperty.getValue(), drs, p,
                            mmd.getTargetClass(), sectionCrumbs);
                    listGrid.getToolbarActions().add(DefaultListGridActions.ADD);

                    if (drs.getUnselectedTabMetadata().get(EntityForm.DEFAULT_TAB_NAME) != null) {
                        entityForm.addListGrid(cmd, listGrid, EntityForm.DEFAULT_TAB_NAME, EntityForm.DEFAULT_TAB_ORDER, fmd.getGroup(), true);
                    } else {
                        entityForm.addListGrid(cmd, listGrid, EntityForm.DEFAULT_TAB_NAME, EntityForm.DEFAULT_TAB_ORDER, fmd.getGroup(), false);
                    }
                }
            }

            formService.populateEntityFormFields(entityForm, entity, populateTypeAndId, populateTypeAndId);
            formService.populateAdornedEntityFormFields(entityForm, entity, ppr.getAdornedList());

            boolean atLeastOneBasicField = false;
            for (Entry<String, Field> entry : entityForm.getFields().entrySet()) {
                if (entry.getValue().getIsVisible() && !responseMap.containsKey(entry.getValue().getName()) && !responseMap.containsKey("autoSubmit")) {
                    atLeastOneBasicField = true;
                    break;
                }
            }
            if (!atLeastOneBasicField) {
                entityForm.removeAction(DefaultEntityFormActions.SAVE);
            }
            addAuditableDisplayFields(entityForm);
            model.addAttribute("entityForm", entityForm);
            model.addAttribute("viewType", "modal/adornedEditEntity");
        } else if (md instanceof MapMetadata) {
            MapMetadata fmd = (MapMetadata) md;

            ClassMetadata collectionMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
            if (entity == null) {
                entity = service.getAdvancedCollectionRecord(mainMetadata, parentEntity, collectionProperty,
                    collectionItemId, sectionCrumbs, null).getEntity();
            }

            boolean populateTypeAndId = true;
            if (entityForm == null) {
                entityForm = formService.buildMapForm(fmd, ppr.getMapStructure(), collectionMetadata, id);
            } else {
                //save off the prior key before clearing out the fields map as it will not appear
                //back on the saved entity
                String priorKey = entityForm.findField("priorKey").getValue();
                entityForm.clearFieldsMap();
                formService.buildMapForm(fmd, ppr.getMapStructure(), collectionMetadata, id, entityForm);
                entityForm.findField("priorKey").setValue(priorKey);
                populateTypeAndId = false;
            }
            try {
                if (StringUtils.isNotEmpty(fmd.getToOneTargetProperty())) {
                    entityForm.setTranslationCeilingEntity(Class.forName(fmd.getValueClassName()).getDeclaredField(fmd.getToOneTargetProperty()).getType().getName());
                }
            } catch (Exception e) {
                LOG.error(e);
            }
            String entityId = (fmd.getToOneTargetProperty().equals("") ? "id" : fmd.getToOneTargetProperty() + ".id");
            entityForm.setTranslationId(entity.getPMap().get(entityId).getValue());
            formService.populateEntityFormFields(entityForm, entity, populateTypeAndId, populateTypeAndId);
            formService.populateMapEntityFormFields(entityForm, entity);
            formService.populateEntityFormFields(entityForm, entity, populateTypeAndId, populateTypeAndId);
            formService.populateMapEntityFormFields(entityForm, entity);
            addAuditableDisplayFields(entityForm);
            model.addAttribute("entityForm", entityForm);
            model.addAttribute("viewType", "modal/mapEditEntity");
        }

        model.addAttribute("currentUrl", request.getRequestURL().toString());
        model.addAttribute("modalHeaderType", modalHeaderType);
        model.addAttribute("collectionProperty", collectionProperty);
        setModelAttributes(model, sectionKey);
        return MODAL_CONTAINER_VIEW;
    }
    
    protected EntityForm reinitializeEntityForm(final EntityForm entityForm, 
            final ClassMetadata collectionMetadata,
            final Entity entity,
            final Map<String, DynamicResultSet> subRecordsMap,
            final List<SectionCrumb> sectionCrumbs) throws ServiceException {
        if (entityForm == null) {
            return formService
                    .createEntityForm(collectionMetadata, entity, subRecordsMap, sectionCrumbs);
        }
        
        entityForm.clearFieldsMap();
        formService.populateEntityForm(collectionMetadata, entity, subRecordsMap, entityForm,
                sectionCrumbs);
        //remove all the actions since we're not trying to redisplay them on the form
        entityForm.removeAllActions();
    
        return entityForm;
    }

    /**
     * Updates the specified collection item
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId the collection primary key value (in the case of adorned target collection, this is the primary key value of the target entity)
     * @param entityForm
     * @param result
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}", method = RequestMethod.POST)
    public String updateCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId,
            @ModelAttribute(value="entityForm") EntityForm entityForm,
            BindingResult result) throws Exception {
        return updateCollectionItem(request, response, model, pathVars, id, collectionField, collectionItemId, entityForm, null, result);
    }

    /**
     * Updates the specified collection item
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId the collection primary key value (in the case of adorned target collection, this is the primary key value of the target entity)
     * @param entityForm
     * @param alternateId in the case of adorned target collections, this is the primary key value of the collection member
     * @param result
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/{alternateId}", method = RequestMethod.POST)
    public String updateCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId,
            @ModelAttribute(value="entityForm") EntityForm entityForm,
            @PathVariable(value="alternateId") String alternateId,
            BindingResult result) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);

        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        Entity entity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];
        service.clearEntityManager();
        // First, we must save the collection entity
        PersistenceResponse persistenceResponse = service.updateSubCollectionEntity(entityForm, mainMetadata, collectionProperty, entity, collectionItemId, alternateId, sectionCrumbs);
        Entity savedEntity = persistenceResponse.getEntity();
        entityFormValidator.validate(entityForm, savedEntity, result);

        if (result.hasErrors()) {
            return showViewUpdateCollection(request, model, pathVars, id, collectionField, collectionItemId, alternateId,
                    ModalHeaderType.UPDATE_COLLECTION_ITEM.getType(), entityForm, savedEntity);
        }

        // Next, we must get the new list grid that represents this collection
        // We return the new list grid so that it can replace the currently visible one
        ListGrid listGrid = getCollectionListGrid(mainMetadata, entity, collectionProperty, null, sectionKey, persistenceResponse, sectionCrumbs);

        model.addAttribute("listGrid", listGrid);
        model.addAttribute("currentUrl", request.getRequestURL().toString());
        setModelAttributes(model, sectionKey);
        return "views/standaloneListGrid";
    }

    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/sequence", method = RequestMethod.POST)
    public @ResponseBody Map<String, Object> updateCollectionItemSequence(HttpServletRequest request,
            HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId,
            @RequestParam(value="newSequence") String newSequence) throws Exception {
        return updateCollectionItemSequence(request, response, model, pathVars, id, collectionField, collectionItemId, newSequence, null);
    }

    /**
     * Updates the given collection item's sequence. This should only be triggered for adorned target collections
     * where a sort field is specified -- any other invocation is incorrect and will result in an exception.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId
     * @return an object explaining the state of the operation
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/{alternateId}/sequence", method = RequestMethod.POST)
    public @ResponseBody Map<String, Object> updateCollectionItemSequence(HttpServletRequest request,
            HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId,
            @RequestParam(value="newSequence") String newSequence,
            @PathVariable(value="alternateId") String alternateId) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);
        FieldMetadata md = collectionProperty.getMetadata();

        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        ppr.addCustomCriteria("reorderParentEntityFetch");

        Entity parentEntity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];

        ppr = PersistencePackageRequest.fromMetadata(md, sectionCrumbs);

        if (md instanceof AdornedTargetCollectionMetadata) {
            AdornedTargetCollectionMetadata fmd = (AdornedTargetCollectionMetadata) md;
            AdornedTargetList atl = ppr.getAdornedList();

            // Get an entity form for the entity
            EntityForm entityForm = formService.buildAdornedListForm(fmd, ppr.getAdornedList(), id, false, sectionCrumbs, false);
            Entity entity = service.getAdvancedCollectionRecord(mainMetadata, parentEntity, collectionProperty,
                    collectionItemId, sectionCrumbs, alternateId, new String[]{"reorderChildEntityFetch"})
                    .getDynamicResultSet().getRecords()[0];
            formService.populateEntityFormFields(entityForm, entity);
            formService.populateAdornedEntityFormFields(entityForm, entity, ppr.getAdornedList());

            // Set the new sequence (note that it will come in 0-indexed but the persistence module expects 1-indexed)
            int sequenceValue = Integer.parseInt(newSequence) + 1;
            Field field = entityForm.findField(atl.getSortField());
            field.setValue(String.valueOf(sequenceValue));

            Map<String, Object> responseMap = new HashMap<>();
            PersistenceResponse persistenceResponse = service.updateSubCollectionEntity(entityForm, mainMetadata, collectionProperty, parentEntity, collectionItemId, alternateId, sectionCrumbs);
            Property displayOrder = persistenceResponse.getEntity().findProperty(atl.getSortField());

            responseMap.put("status", "ok");
            responseMap.put("field", collectionField);
            responseMap.put("newDisplayOrder", displayOrder == null ? null : displayOrder.getValue());
            return responseMap;
        } else if (md instanceof BasicCollectionMetadata) {
            BasicCollectionMetadata cd = (BasicCollectionMetadata) md;
            Map<String, Object> responseMap = new HashMap<>();
            Entity entity = service.getRecord(ppr, collectionItemId, mainMetadata, false).getDynamicResultSet().getRecords()[0];

            ClassMetadata collectionMetadata = service.getClassMetadata(ppr).getDynamicResultSet().getClassMetaData();
            EntityForm entityForm = formService.createEntityForm(collectionMetadata, sectionCrumbs);
            boolean listGridReadOnly = !rowLevelSecurityService.canUpdate(adminRemoteSecurityService.getPersistentAdminUser(), entity);
            if(listGridReadOnly){
                        throw new SecurityServiceException();
            }
            if (!StringUtils.isEmpty(cd.getSortProperty())) {
                Field f = new Field()
                        .withName(cd.getSortProperty())
                        .withFieldType(SupportedFieldType.HIDDEN.toString());
                entityForm.addHiddenField(mainMetadata, f);
            }
            formService.populateEntityFormFields(entityForm, entity);

            if (!StringUtils.isEmpty(cd.getSortProperty())) {
                int sequenceValue = Integer.parseInt(newSequence) + 1;
                Field field = entityForm.findField(cd.getSortProperty());
                field.setValue(String.valueOf(sequenceValue));
            }

            PersistenceResponse persistenceResponse = service.updateSubCollectionEntity(entityForm, mainMetadata, collectionProperty, parentEntity, collectionItemId, sectionCrumbs);
            Property displayOrder = persistenceResponse.getEntity().findProperty(cd.getSortProperty());

            responseMap.put("status", "ok");
            responseMap.put("field", collectionField);
            responseMap.put("newDisplayOrder", displayOrder == null ? null : displayOrder.getValue());
            return responseMap;
        } else {
            throw new UnsupportedOperationException("Cannot handle sequencing for non adorned target collection fields.");
        }
    }

    /**
     * Removes the requested collection item
     *
     * Note that the request must contain a parameter called "key" when attempting to remove a collection item from a
     * map collection.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/delete", method = RequestMethod.POST)
    public String removeCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId) throws Exception {
        return removeCollectionItem(request, response, model, pathVars, id, collectionField, collectionItemId, null);
    }

    /**
     * Removes the requested collection item
     *
     * Note that the request must contain a parameter called "key" when attempting to remove a collection item from a
     * map collection.
     *
     * @param request
     * @param response
     * @param model
     * @param pathVars
     * @param id
     * @param collectionField
     * @param collectionItemId
     * @return the return view path
     * @throws Exception
     */
    @RequestMapping(value = "/{id}/{collectionField:.*}/{collectionItemId}/{alternateId}/delete", method = RequestMethod.POST)
    public String removeCollectionItem(HttpServletRequest request, HttpServletResponse response, Model model,
            @PathVariable  Map<String, String> pathVars,
            @PathVariable(value="id") String id,
            @PathVariable(value="collectionField") String collectionField,
            @PathVariable(value="collectionItemId") String collectionItemId,
            @PathVariable(value="alternateId") String alternateId) throws Exception {
        String sectionKey = getSectionKey(pathVars);
        String mainClassName = getClassNameForSection(sectionKey);
        List<SectionCrumb> sectionCrumbs = getSectionCrumbs(request, sectionKey, id);
        ClassMetadata mainMetadata = service.getClassMetadata(getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars)).getDynamicResultSet().getClassMetaData();
        Property collectionProperty = mainMetadata.getPMap().get(collectionField);

        String priorKey = request.getParameter("key");

        PersistencePackageRequest ppr = getSectionPersistencePackageRequest(mainClassName, sectionCrumbs, pathVars);
        declareShouldIgnoreAdditionStatusFilter();
        Entity entity = service.getRecord(ppr, id, mainMetadata, false).getDynamicResultSet().getRecords()[0];
        service.clearEntityManager();
        // First, we must remove the collection entity
        PersistenceResponse persistenceResponse = service.removeSubCollectionEntity(mainMetadata, collectionProperty, entity, collectionItemId, alternateId, priorKey, sectionCrumbs);
        if (persistenceResponse.getEntity() != null && persistenceResponse.getEntity().isValidationFailure()) {
            String error = "There was an error removing the whatever";
            if (MapUtils.isNotEmpty(persistenceResponse.getEntity().getPropertyValidationErrors())) {
                // If we failed, we'll return some JSON with the first error
                error = persistenceResponse.getEntity().getPropertyValidationErrors().values().iterator().next().get(0);
            } else if (CollectionUtils.isNotEmpty(persistenceResponse.getEntity().getGlobalValidationErrors())) {
                error = persistenceResponse.getEntity().getGlobalValidationErrors().get(0);
            }
            return new JsonResponse(response)
                .with("status", "error")
                .with("message", BLCMessageUtils.getMessage(error))
                .done();
        }

        // Next, we must get the new list grid that represents this collection
        // We return the new list grid so that it can replace the currently visible one
        ListGrid listGrid = getCollectionListGrid(mainMetadata, entity, collectionProperty, null, sectionKey, persistenceResponse, sectionCrumbs);

        model.addAttribute("listGrid", listGrid);
        model.addAttribute("currentUrl", request.getRequestURL().toString());
        setModelAttributes(model, sectionKey);
        return "views/standaloneListGrid";
    }

    public void addAuditableDisplayFields(EntityForm entityForm) {
        Field createdBy = entityForm.findField("auditable.createdBy");
        if (createdBy != null && createdBy.getValue() != null) {
            addAuditableDisplayField(entityForm, createdBy);

            createdBy.setIsVisible(false);
        }
        Field updatedBy = entityForm.findField("auditable.updatedBy");
        if (updatedBy != null && updatedBy.getValue() != null) {
            addAuditableDisplayField(entityForm, updatedBy);

            updatedBy.setIsVisible(false);
        }
    }

    private void addAuditableDisplayField(EntityForm entityForm, Field userField) {
        Field displayField = buildAuditableDisplayField(userField);

        AdminUser user = adminUserDao.readAdminUserById(Long.parseLong(userField.getValue()));
        String userName = user == null ? null : user.getName();
        displayField.setValue(userName);

        FieldGroup auditGroup = entityForm.findGroup("AdminAuditable_Audit");
        if (auditGroup != null) {
            auditGroup.addField(displayField);
        }
    }

    private Field buildAuditableDisplayField(Field auditableField) {
        return new Field().withFieldType(SupportedFieldType.STRING.toString())
                .withName(auditableField.getName() + "Display")
                .withFriendlyName(auditableField.getFriendlyName())
                .withOrder(auditableField.getOrder())
                .withOwningEntityClass(auditableField.getOwningEntityClass())
                .withReadOnly(true);
    }

    protected String getCurrentTabName(Map<String, String> pathVars, ClassMetadata cmd) {
        String tabName = pathVars.get("tabName");
        if (StringUtils.isBlank(tabName)) {
            TabMetadata firstTab = cmd.getFirstTab();
            return firstTab != null ? firstTab.getTabName() : "General";
        }
        return tabName;
    }

    protected String getCurrentFolderId(HttpServletRequest request) {
        if (request.getParameterMap().containsKey(CURRENT_FOLDER_ID)) {
            return request.getParameter(CURRENT_FOLDER_ID);
        }
        return "unassigned";
    }

    // *****************************************
    // Typed Entity Helper Methods
    // *****************************************

    protected void setTypedEntityModelAttributes(HttpServletRequest request, Model model) {
        // Check if this is a typed entity
        AdminSection typedEntitySection = (AdminSection) request.getAttribute("typedEntitySection");
        if (typedEntitySection != null) {
            // Update the friendly name for this Entity Type
            model.addAttribute("entityFriendlyName", typedEntitySection.getName());
        }
    }

    // *********************************
    // ADDITIONAL SPRING-BOUND METHODS *
    // *********************************

    /**
     * Invoked on every request to provide the ability to register specific binders for Spring's binding process.
     * By default, we register a binder that treats empty Strings as null and a Boolean editor that supports either true
     * or false. If the value is passed in as null, it will treat it as false.
     *
     * @param binder
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
        binder.registerCustomEditor(Boolean.class, new NonNullBooleanEditor());
    }

}
