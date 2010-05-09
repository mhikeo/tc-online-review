/*
 * Copyright (C) 2004 - 2010 TopCoder Inc., All Rights Reserved.
 */
package com.cronos.onlinereview.actions;

import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.topcoder.management.phase.ContestDependencyAutomation;
import com.topcoder.management.project.link.ProjectLinkManager;
import com.topcoder.web.common.eligibility.ContestEligibilityServiceLocator;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.actions.DispatchAction;
import org.apache.struts.util.MessageResources;
import org.apache.struts.validator.LazyValidatorForm;

import com.cronos.onlinereview.external.ExternalUser;
import com.cronos.onlinereview.external.UserRetrieval;
import com.topcoder.date.workdays.DefaultWorkdaysFactory;
import com.topcoder.date.workdays.Workdays;
import com.topcoder.date.workdays.WorkdaysUnitOfTime;
import com.topcoder.management.deliverable.Deliverable;
import com.topcoder.management.deliverable.DeliverableManager;
import com.topcoder.management.deliverable.persistence.DeliverableCheckingException;
import com.topcoder.management.deliverable.persistence.DeliverablePersistenceException;
import com.topcoder.management.phase.PhaseManager;
import com.topcoder.management.project.Project;
import com.topcoder.management.project.ProjectCategory;
import com.topcoder.management.project.ProjectFilterUtility;
import com.topcoder.management.project.ProjectManager;
import com.topcoder.management.project.ProjectStatus;
import com.topcoder.management.project.ProjectType;
import com.topcoder.management.resource.NotificationType;
import com.topcoder.management.resource.Resource;
import com.topcoder.management.resource.ResourceManager;
import com.topcoder.management.resource.ResourceRole;
import com.topcoder.management.resource.search.ResourceFilterBuilder;
import com.topcoder.management.scorecard.ScorecardManager;
import com.topcoder.management.scorecard.ScorecardSearchBundle;
import com.topcoder.management.scorecard.data.Scorecard;
import com.topcoder.project.phases.CyclicDependencyException;
import com.topcoder.project.phases.Dependency;
import com.topcoder.project.phases.Phase;
import com.topcoder.project.phases.PhaseStatus;
import com.topcoder.project.phases.PhaseType;
import com.topcoder.search.builder.SearchBuilderException;
import com.topcoder.search.builder.filter.AndFilter;
import com.topcoder.search.builder.filter.Filter;
import com.topcoder.search.builder.filter.InFilter;
import com.topcoder.service.contest.eligibilityvalidation.ContestEligibilityValidatorException;
import com.topcoder.shared.util.DBMS;
import com.topcoder.util.errorhandling.BaseException;
import com.topcoder.web.ejb.project.ProjectRoleTermsOfUse;
import com.topcoder.web.ejb.project.ProjectRoleTermsOfUseLocator;
import com.topcoder.web.ejb.termsofuse.TermsOfUse;
import com.topcoder.web.ejb.termsofuse.TermsOfUseEntity;
import com.topcoder.web.ejb.termsofuse.TermsOfUseLocator;
import com.topcoder.web.ejb.user.UserTermsOfUse;
import com.topcoder.web.ejb.user.UserTermsOfUseLocator;

import static com.cronos.onlinereview.actions.Constants.REGISTRATION_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.SUBMISSION_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.SCREENING_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.REVIEW_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.APPEALS_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.APPEALS_RESPONSE_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.AGGREGATION_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.AGGREGATION_REVIEW_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.FINAL_FIX_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.FINAL_REVIEW_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.APPROVAL_PHASE_NAME;
import static com.cronos.onlinereview.actions.Constants.POST_MORTEM_PHASE_NAME;

/**
 * This class contains Struts Actions that are meant to deal with Projects. There are following
 * Actions defined in this class:
 * <ul>
 * <li>New Project</li>
 * <li>Edit Project</li>
 * <li>Save Project</li>
 * <li>List Projects</li>
 * </ul>
 * <p>
 * This class is thread-safe as it does not contain any mutable inner state.
 * </p>
 *
 * <p>
 * Version 1.1 (Configurable Contest Terms Release Assembly v1.0) Change notes:
 *   <ol>
 *     <li>Added Project Role User Terms Of Use association generation to project creation.</li>
 *     <li>Added Project Role User Terms Of Use verification when adding/updating project resources.</li>
 *   </ol>
 * </p>
 *
 * <p>
 * Version 1.2 (Appeals Early Completion Release Assembly 1.0) Change notes:
 *   <ol>
 *     <li>Added Appeals Completed Early flag manipulation when project is saved.</li>
 *   </ol>
 * </p>
 *
 * <p>
 * Version 1.3 (Appeals Early Completion Release Assembly 2.0) Change notes:
 *   <ol>
 *     <li>Added sort order parameter to project role terms of use service call.</li>
 *   </ol>
 * </p>
 *
 * <p>
 * Version 1.4 (Competition Registration Eligibility v1.0) Change notes:
 *   <ol>
 *     <li>Removed old "Public" and "Eligibility" project info code. Public projects are now determined by contest
 *         eligibility service.</li>
 *     <li>Added contest eligibility validation to <code>checkForCorrectProjectId</code> method.</li>
 *   </ol>
 * </p>
 *
 * <p>
 * Version 1.5 (Contest Dependency Automation v1.0) Change notes:
 *   <ol>
 *     <li>
 *       Updated {@link #saveProjectPhases(boolean, HttpServletRequest, LazyValidatorForm, Project, Map, List, boolean)} 
 *       method to adjust the start times (if necessary) for projects which depend on current project being updated.
 *     </li>
 *   </ol>
 * </p>
 *
 * <p>
 * Version 1.6 (Online Review End Of Project Analysis Assembly 1.0) Change notes:
 *   <ol>
 *     <li>
 *       Updated {@link #saveProject(ActionMapping, ActionForm, HttpServletRequest, HttpServletResponse)} method to
 *       validate that category selected for project is not generic one.
 *     </li>
 *     <li>
 *       Updated {@link #validateProjectPhases(HttpServletRequest, Project, Phase[])} method to take into consideration
 *       <code>Approval</code> and <code>Post-Mortem</code> phases.  
 *     </li>
 *   </ol>
 * </p>
 *
 * @author George1, real_vg, pulky, isv
 * @version 1.6
 */
public class ProjectActions extends DispatchAction {

    /**
     * This constant stores development project type id
     *
     * @since 1.1
     */
    private static final int DEVELOPMENT_PROJECT_TYPE_ID = 2;

    /**
     * Default sort order for project role terms of use generation
     *
     * @since 1.3
     */
    private static final int DEFAULT_TERMS_SORT_ORDER = 1;

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("MM.dd.yyyy hh:mm a", Locale.US);

    /**
     * Creates a new instance of the <code>ProjectActions</code> class.
     */
    public ProjectActions() {
    }

    /**
     * This method is an implementation of &quot;New Project&quot; Struts Action defined for this
     * assembly, which is supposed to fetch lists of project types and categories from the database
     * and pass it to the JSP page to use it for populating approprate drop down lists.
     *
     * @return &quot;success&quot; forward that forwards to the /jsp/editProject.jsp page (as
     *         defined in struts-config.xml file) in the case of successfull processing,
     *         &quot;notAuthorized&quot; forward in the case of user not being authorized to perform
     *         the action.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             when any error happens while processing in TCS components
     */
    public ActionForward newProject(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
        throws BaseException {
        LoggingHelper.logAction(request);
        // Gather the roles the user has for current request
        AuthorizationHelper.gatherUserRoles(request);

        // Check if the user has the permission to perform this action
        if (!AuthorizationHelper.hasUserPermission(request, Constants.CREATE_PROJECT_PERM_NAME)) {
            // If he doesn't, redirect the request to login page or report about the lack of permissions
            return ActionsHelper.produceErrorReport(mapping, getResources(request), request,
                    Constants.CREATE_PROJECT_PERM_NAME, "Error.NoPermission", Boolean.FALSE);
        }
        // At this point, redirect-after-login attribute should be removed (if it exists)
        AuthorizationHelper.removeLoginRedirect(request);

        // Place the index of the active tab into the request
        request.setAttribute("projectTabIndex", new Integer(3));
        // Place the flag, indicating that we are creating a new project, into request
        request.setAttribute("newProject", Boolean.TRUE);

        LazyValidatorForm formNewProject = (LazyValidatorForm) form;

        // Make 'Send Email Notifications' and
        // 'Receive Timeline Notifications' checkboxes be checked by default
        formNewProject.set("email_notifications", Boolean.TRUE);
        formNewProject.set("timeline_notifications", Boolean.TRUE);

        // Load the look up data
        loadProjectEditLookups(request);

        // Populate the default values of some project form fields
        populateProjectFormDefaults(formNewProject, request);

        // Return the success forward
        return mapping.findForward(Constants.SUCCESS_FORWARD_NAME);
    }

    /**
     * TODO: Document this method.
     *
     * @param lazyForm
     * @param request
     */
    private void populateProjectFormDefaults(LazyValidatorForm lazyForm, HttpServletRequest request) {
        // Set the JS id to start generation from
        lazyForm.set("js_current_id", new Long(0));

        // Populate form with some data so that resources row template
        // is rendered properly by the appropriate JSP
        lazyForm.set("resources_role", 0, new Long(-1));
        lazyForm.set("resources_id", 0, new Long(-1));
        lazyForm.set("resources_action", 0, "add");

        // Populate form with some data so that phases row template
        // is rendered properly by the appropriate JSP
        lazyForm.set("phase_id", 0, new Long(-1));
        lazyForm.set("phase_action", 0, "add");
        lazyForm.set("phase_can_open", 0, Boolean.TRUE);
        lazyForm.set("phase_can_close", 0, Boolean.FALSE);
        lazyForm.set("phase_use_duration", 0, Boolean.TRUE);

        // Populate some phase criteria with default values read from the configuration
        if (ConfigHelper.getDefaultRequiredRegistrants() >= 0) {
            lazyForm.set("phase_required_registrations", 0, new Integer(ConfigHelper.getDefaultRequiredRegistrants()));
        }
        if (ConfigHelper.getDefaultRequiredSubmissions() >= 0) {
            lazyForm.set("phase_required_submissions", 0, new Integer(ConfigHelper.getDefaultRequiredSubmissions()));
        }
        if (ConfigHelper.getDefaultRequiredReviewers() >= 0) {
            lazyForm.set("phase_required_reviewers", 0, new Integer(ConfigHelper.getDefaultRequiredReviewers()));
        }
        if (ConfigHelper.getDefaultRequiredApprovers() >= 0) {
            request.setAttribute("phase_required_reviewers_approval",
                                 new Integer(ConfigHelper.getDefaultRequiredApprovers()));
        }
        if (ConfigHelper.getDefaultRequiredPostMortemReviewers() >= 0) {
            request.setAttribute("phase_required_reviewers_postmortem",
                                 new Integer(ConfigHelper.getDefaultRequiredPostMortemReviewers()));
        }

        // Populate default phase duration
        lazyForm.set("addphase_duration", new Integer(ConfigHelper.getDefaultPhaseDuration()));
    }

    /**
     * This method loads the lookup data needed for rendering the Create Project/New Project pages.
     * The loaded data is stored in the request attributes.
     *
     * <p>
     * Updated for Online Review Update - Add Project Dropdown v1.0
     *      Added retrieval of billing projects.
     * </p>
     *
     * @param request the request to load the lookup data into
     * @throws BaseException if any error occurs while loading the lookup data
     */
    private void loadProjectEditLookups(HttpServletRequest request) throws BaseException {
        // Obtain an instance of Project Manager
        ProjectManager projectManager = ActionsHelper.createProjectManager(request);

        // Retrieve project types and categories
        ProjectType[] projectTypes = projectManager.getAllProjectTypes();
        ProjectCategory[] projectCategories = projectManager.getAllProjectCategories();

        // Store the retrieved types and categories in request
        request.setAttribute("projectTypes", projectTypes);
        request.setAttribute("projectCategories", projectCategories);
        request.setAttribute("projectCategoriesMap", buildProjectCategoriesLookupMap(projectCategories));

        // Obtain an instance of Resource Manager
        ResourceManager resourceManager = ActionsHelper.createResourceManager(request);
        // Get all types of resource roles
        ResourceRole[] resourceRoles = resourceManager.getAllResourceRoles();
        // Place resource roles into the request as attribute
        request.setAttribute("resourceRoles", resourceRoles);

        // Obtain an instance of Phase Manager
        PhaseManager phaseManager = ActionsHelper.createPhaseManager(request, false);
        // Get all phase types
        PhaseType[] phaseTypes = phaseManager.getAllPhaseTypes();
        // Place them into request as an attribute
        request.setAttribute("phaseTypes", phaseTypes);

        // Obtain an instance of Scorecard Manager
        ScorecardManager scorecardManager = ActionsHelper.createScorecardManager(request);

        // TODO: Check if we need to filter by the project category
        // Retrieve the scorecard lists
        Scorecard[] screeningScorecards = searchActiveScorecards(scorecardManager, "Screening");
        Scorecard[] reviewScorecards = searchActiveScorecards(scorecardManager, "Review");
        Scorecard[] approvalScorecards = searchActiveScorecards(scorecardManager, "Approval");
        Scorecard[] postMortemScorecards = searchActiveScorecards(scorecardManager, "Post-Mortem");

        // Store them in the request
        request.setAttribute("screeningScorecards", screeningScorecards);
        request.setAttribute("reviewScorecards", reviewScorecards);
        request.setAttribute("approvalScorecards", approvalScorecards);
        request.setAttribute("postMortemScorecards", postMortemScorecards);
        request.setAttribute("defaultScorecards", ActionsHelper.getDefaultScorecards());

        // Load phase template names
        String[] phaseTemplateNames = ActionsHelper.createPhaseTemplate(null).getAllTemplateNames();
        request.setAttribute("phaseTemplateNames", phaseTemplateNames);

        // since Online Review Update - Add Project Dropdown v1.0
        // Retrieve the list of all client projects and store it in the request
        // this need to be retrieved only for admin user.
        if (AuthorizationHelper.hasUserRole(request, Constants.MANAGER_ROLE_NAME)
                 || AuthorizationHelper.hasUserRole(request, Constants.GLOBAL_MANAGER_ROLE_NAME)) {
            request.setAttribute("billingProjects", ActionsHelper.getClientProjects(request));
        }
    }

    /**
     * TODO: Document it
     * Note, that the scorecard data(items) is not fully retrieved
     *
     * @param scorecardManager
     * @param scorecardTypeName
     * @return
     * @throws BaseException
     */
    private Scorecard[] searchActiveScorecards(ScorecardManager scorecardManager, String scorecardTypeName) throws BaseException {
        Filter filter = ScorecardSearchBundle.buildAndFilter(
                ScorecardSearchBundle.buildTypeNameEqualFilter(scorecardTypeName),
                ScorecardSearchBundle.buildStatusNameEqualFilter("Active"));
        return scorecardManager.searchScorecards(filter, false);
    }

    /**
     * This method populates the specified LazyValidatorForm with the values
     * taken from the specified Project.
     *
     * <p>
     * Updated for Online Review Update - Add Project Dropdown v1.0
     *      - Set the 'Billing Project' value to form's billing_project property.
     *      - Set the isAdmin property.
     * </p>
     *
     * @param request
     *            the request to be processed
     * @param form
     *            the form to be populated with data
     * @param project
     *            the project to take the data from
     * @throws BaseException
     */
    private void populateProjectForm(HttpServletRequest request, LazyValidatorForm form, Project project)
        throws BaseException {
        // TODO: Possibly use string constants instead of hardcoded strings

        // Populate project id
        form.set("pid", new Long(project.getId()));

        // Populate project name
        populateProjectFormProperty(form, String.class, "project_name", project, "Project Name");

        // Populate project type
        Long projectTypeId = new Long(project.getProjectCategory().getProjectType().getId());
        form.set("project_type", projectTypeId);

        // Populate project category
        Long projectCategoryId = new Long(project.getProjectCategory().getId());
        form.set("project_category", projectCategoryId);

        // Populate project category
        Long projectStatusId = new Long(project.getProjectStatus().getId());
        form.set("status", projectStatusId);

        // Populate project forum id
        populateProjectFormProperty(form, Long.class, "forum_id", project, "Developer Forum ID");

        // Populate project component id
        populateProjectFormProperty(form, Long.class, "component_id", project, "Component ID");
        // Populate project external reference id
        populateProjectFormProperty(form, Long.class, "external_reference_id", project, "External Reference ID");
        // Populate project price
        populateProjectFormProperty(form, Double.class, "payments", project, "Payments");
        // Populate project dr points
        populateProjectFormProperty(form, Double.class, "dr_points", project, "DR points");

        // since Online Review Update - Add Project Dropdown v1.0
        // Populate project billing project
        populateProjectFormProperty(form, Long.class, "billing_project", project, "Billing Project");

        // Populate project autopilot option
        form.set("autopilot", new Boolean("On".equals(project.getProperty("Autopilot Option"))));
        // Populate project status notification option
        form.set("email_notifications", new Boolean("On".equals(project.getProperty("Status Notification"))));
        // Populate project timeline notification option
        form.set("timeline_notifications", new Boolean("On".equals(project.getProperty("Timeline Notification"))));
        // Populate project Digital Run option
        form.set("digital_run_flag", new Boolean("On".equals(project.getProperty("Digital Run Flag"))));
        // Populate project's 'do not rate this project' option
        // Note, this property is inverse by its meaning in project and form
        form.set("no_rate_project", new Boolean(!("Yes".equals(project.getProperty("Rated")))));

        // Populate project SVN module
        populateProjectFormProperty(form, String.class, "SVN_module", project, "SVN Module");
        // Populate project notes
        populateProjectFormProperty(form, String.class, "notes", project, "Notes");

        // Populate the default values of some project form fields
        populateProjectFormDefaults(form, request);

        // Obtain Resource Manager instance
        ResourceManager resourceManager = ActionsHelper.createResourceManager(request);

        // Retreive the list of the resources associated with the project
        Resource[] resources =
            resourceManager.searchResources(ResourceFilterBuilder.createProjectIdFilter(project.getId()));
        // Get an array of external users for the corresponding resources
        ExternalUser[] externalUsers =
            ActionsHelper.getExternalUsersForResources(ActionsHelper.createUserRetrieval(request), resources);

        // Populate form with resources data
        for (int i = 0; i < resources.length; ++i) {
            form.set("resources_id", i + 1, new Long(resources[i].getId()));
            form.set("resources_action", i + 1, "update");

            form.set("resources_role", i + 1, new Long(resources[i].getResourceRole().getId()));
            form.set("resources_phase", i + 1, "loaded_" + resources[i].getPhase());
            form.set("resources_name", i + 1, externalUsers[i].getHandle());

            if (resources[i].getProperty("Payment") != null) {
                form.set("resources_payment", i + 1, Boolean.TRUE);
                form.set("resources_payment_amount", i + 1, Double.valueOf((String) resources[i].getProperty("Payment")));
            } else {
                form.set("resources_payment", i + 1, Boolean.FALSE);
            }

            if (resources[i].getProperty("Payment Status") != null) {
                form.set("resources_paid", i + 1, resources[i].getProperty("Payment Status"));
            } else {
                form.set("resources_paid", i + 1, "N/A");
            }
        }

        // Obtain Phase Manager instance
        PhaseManager phaseManager = ActionsHelper.createPhaseManager(request, false);

        // Retrieve project phases
        Phase[] phases = ActionsHelper.getPhasesForProject(phaseManager, project);
        // Sort project phases
        Arrays.sort(phases, new Comparators.ProjectPhaseComparer());

        Map<Long, Integer> phaseNumberMap = new HashMap<Long, Integer>();

        // Populate form with phases data
        for (int i = 0; i < phases.length; ++i) {
            form.set("phase_id", i + 1, new Long(phases[i].getId()));

            form.set("phase_can_open", i + 1,
                    Boolean.valueOf(phases[i].getPhaseStatus().getName().equals(PhaseStatus.SCHEDULED.getName())));
            form.set("phase_can_close", i + 1,
                    Boolean.valueOf(phases[i].getPhaseStatus().getName().equals(PhaseStatus.OPEN.getName())));

            Long phaseTypeId = new Long(phases[i].getPhaseType().getId());
            form.set("phase_type", i + 1, phaseTypeId);
            Integer phaseNumber = (Integer) phaseNumberMap.get(phaseTypeId);
            if (phaseNumber == null) {
                phaseNumber = new Integer(1);
            } else {
                phaseNumber = new Integer(phaseNumber.intValue() + 1);
            }
            phaseNumberMap.put(phaseTypeId, phaseNumber);
            form.set("phase_number", i + 1, phaseNumber);

            form.set("phase_name", i + 1, phases[i].getPhaseType().getName());
            form.set("phase_action", i + 1, "update");
            form.set("phase_js_id", i + 1, "loaded_" + phases[i].getId());
            if (phases[i].getAllDependencies().length > 0) {
                form.set("phase_start_by_phase", i + 1, Boolean.TRUE);
                // TODO: Probably will need to rewrite all those dependency stuff
                // TODO: It is very incomplete actually
                Dependency dependency = phases[i].getAllDependencies()[0];
                form.set("phase_start_phase", i + 1, "loaded_" + dependency.getDependency().getId());
                form.set("phase_start_amount", i + 1, new Integer((int) (dependency.getLagTime() / 3600 / 1000)));
                form.set("phase_start_when", i + 1, dependency.isDependencyStart() ? "starts" : "ends");
                form.set("phase_start_dayshrs", i + 1, "hrs");
            } else {
                form.set("phase_start_by_phase", i + 1, Boolean.FALSE);
            }

            populateDatetimeFormProperties(form, "phase_start_date", "phase_start_time", "phase_start_AMPM", i + 1,
                    phases[i].calcStartDate());

            populateDatetimeFormProperties(form, "phase_end_date", "phase_end_time", "phase_end_AMPM", i + 1,
                    phases[i].calcEndDate());
            // always use duration
            form.set("phase_use_duration", i + 1, Boolean.TRUE);

            // populate the phase duration
            long phaseLength = phases[i].getLength();
            String phaseDuration = "";
            if (phaseLength % (3600*1000) == 0) {
                phaseDuration = "" + phaseLength / (3600 * 1000);
            } else {
                long hour = phaseLength / 3600 / 1000;
                long min = (phaseLength % (3600 * 1000)) / 1000 / 60;
                phaseDuration = hour + ":" + (min >= 10 ? "" + min : "0" + min);
            }

            form.set("phase_duration", i + 1, phaseDuration);

            // Populate phase criteria
            if (phases[i].getAttribute("Scorecard ID") != null) {
                form.set("phase_scorecard", i + 1, Long.valueOf((String) phases[i].getAttribute("Scorecard ID")));
            }
            if (phases[i].getAttribute("Registration Number") != null) {
                form.set("phase_required_registrations", i + 1,
                        Integer.valueOf((String) phases[i].getAttribute("Registration Number")));
            }
            if (phases[i].getAttribute("Submission Number") != null) {
                form.set("phase_required_submissions", i + 1,
                        Integer.valueOf((String) phases[i].getAttribute("Submission Number")));
                form.set("phase_manual_screening", i + 1,
                        Boolean.valueOf("Yes".equals(phases[i].getAttribute("Manual Screening"))));
            }
            if (phases[i].getAttribute("Reviewer Number") != null) {
                form.set("phase_required_reviewers", i + 1,
                        Integer.valueOf((String) phases[i].getAttribute("Reviewer Number")));
            }
            if (phases[i].getAttribute("View Response During Appeals") != null) {
                form.set("phase_view_appeal_responses", i + 1,
                        Boolean.valueOf("Yes".equals(phases[i].getAttribute("View Response During Appeals"))));
            }
        }

        PhasesDetails phasesDetails = PhasesDetailsServices.getPhasesDetails(
                request, getResources(request), project, phases, resources, externalUsers);

        request.setAttribute("phaseGroupIndexes", phasesDetails.getPhaseGroupIndexes());
        request.setAttribute("phaseGroups", phasesDetails.getPhaseGroups());
        request.setAttribute("activeTabIdx", phasesDetails.getActiveTabIndex());
        request.setAttribute("passingMinimum", new Float(75.0)); // TODO: Take this value from scorecard template

        request.setAttribute("isManager",
                Boolean.valueOf(AuthorizationHelper.hasUserRole(request, Constants.MANAGER_ROLE_NAMES)));
        request.setAttribute("isAllowedToPerformScreening",
                Boolean.valueOf(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_SCREENING_PERM_NAME) &&
                        ActionsHelper.getPhase(phases, true, Constants.SCREENING_PHASE_NAME) != null));
        request.setAttribute("isAllowedToViewScreening",
                Boolean.valueOf(AuthorizationHelper.hasUserPermission(request, Constants.VIEW_SCREENING_PERM_NAME)));
        request.setAttribute("isAllowedToUploadTC",
                Boolean.valueOf(AuthorizationHelper.hasUserPermission(request, Constants.UPLOAD_TEST_CASES_PERM_NAME)));
        request.setAttribute("isAllowedToPerformAggregation",
                Boolean.valueOf(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_AGGREGATION_PERM_NAME)));
        request.setAttribute("isAllowedToPerformAggregationReview",
                Boolean.valueOf(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_AGGREG_REVIEW_PERM_NAME) &&
                        !AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_AGGREGATION_PERM_NAME)));
        request.setAttribute("isAllowedToUploadFF",
                Boolean.valueOf(AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_FINAL_FIX_PERM_NAME)));
        request.setAttribute("isAllowedToPerformFinalReview",
                Boolean.valueOf(ActionsHelper.getPhase(phases, true, Constants.FINAL_REVIEW_PHASE_NAME) != null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_FINAL_REVIEW_PERM_NAME)));
        request.setAttribute("isAllowedToPerformApproval",
                Boolean.valueOf(ActionsHelper.getPhase(phases, true, Constants.APPROVAL_PHASE_NAME) != null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_APPROVAL_PERM_NAME)));
        request.setAttribute("isAllowedToPerformPortMortemReview",
                Boolean.valueOf(ActionsHelper.getPhase(phases, true, Constants.POST_MORTEM_PHASE_NAME) != null &&
                        AuthorizationHelper.hasUserPermission(request, Constants.PERFORM_POST_MORTEM_REVIEW_PERM_NAME)));

        // since Online Review Update - Add Project Dropdown v1.0
        request.setAttribute("isAdmin",
                Boolean.valueOf(AuthorizationHelper.hasUserRole(request, Constants.MANAGER_ROLE_NAME) ||
                                    AuthorizationHelper.hasUserRole(request, Constants.GLOBAL_MANAGER_ROLE_NAME)));
    }

    /**
     * TODO: Document it
     *
     * @param form
     * @param dateProperty
     * @param timeProperty
     * @param ampmProperty
     * @param index
     * @param date
     */
    private void populateDatetimeFormProperties(LazyValidatorForm form, String dateProperty, String timeProperty,
            String ampmProperty, int index, Date date) {
        // TODO: Reuse the DateFormat instance
        DateFormat dateFormat = new SimpleDateFormat("MM.dd.yy hh:mm aa", Locale.US);
        String[] parts = dateFormat.format(date).split("[ ]");
        form.set(dateProperty, index, parts[0]);
        form.set(timeProperty, index, parts[1]);
        form.set(ampmProperty, index, parts[2].toLowerCase());
    }

    /**
     * This method populates as single property of the project form by the value taken from the
     * specified Project instance.
     *
     * @param form
     *            the form to populate property of
     * @param type
     *            the type of form property to be populated
     * @param formProperty
     *            the name of form property to be populated
     * @param project
     *            the project to take the value of property of
     * @param projectProperty
     *            the name of project property to take the value of
     */
    private void populateProjectFormProperty(LazyValidatorForm form, Class type, String formProperty,
            Project project, String projectProperty) {

        String value = (String) project.getProperty(projectProperty);
        if (value != null) {
            if (type == String.class) {
                form.set(formProperty, value);
            } else if (type == Boolean.class) {
                form.set(formProperty, Boolean.valueOf(value.compareToIgnoreCase("Yes") == 0));
            } else if (type == Long.class) {
                form.set(formProperty, Long.valueOf(value));
            } else if (type == Integer.class) {
                form.set(formProperty, Integer.valueOf(value));
            } else if (type == Double.class) {
                form.set(formProperty, Double.valueOf(value));
            }
        }
    }

    /**
     * This method is an implementation of &quot;Edit Project&quot; Struts Action defined for this
     * assembly, which is supposed to fetch lists of project types and categories from the database
     * and pass it to the JSP page to use it for populating approprate drop down lists. It is also
     * supposed to retrieve the project to be edited and to populate the form with appropriate
     * values.
     *
     * @return &quot;success&quot; forward that forwards to the /jsp/editProject.jsp page (as
     *         defined in struts-config.xml file) in the case of successfull processing,
     *         &quot;notAuthorized&quot; forward in the case of user not being authorized to perform
     *         the action.
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     *             when any error happens while processing in TCS components.
     */
    public ActionForward editProject(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws BaseException {
        LoggingHelper.logAction(request);

        // Verify that certain requirements are met before processing with the Action
        CorrectnessCheckResult verification = ActionsHelper.checkForCorrectProjectId(
                mapping, getResources(request), request, Constants.EDIT_PROJECT_DETAILS_PERM_NAME, false);
        // If any error has occurred, return action forward contained in the result bean
        if (!verification.isSuccessful()) {
            return verification.getForward();
        }

        // Place the flag, indicating that we are editing the existing project, into request
        request.setAttribute("newProject", Boolean.FALSE);

        // Load the lookup data
        loadProjectEditLookups(request);

        // Obtain an instance of Project Manager
        ProjectManager manager = ActionsHelper.createProjectManager(request);
        // Retrieve the list of all project statuses
        ProjectStatus[] projectStatuses = manager.getAllProjectStatuses();
        // Store it in the request
        request.setAttribute("projectStatuses", projectStatuses);

        // Populate the form with project properties
        populateProjectForm(request, (LazyValidatorForm) form, verification.getProject());

        return mapping.findForward(Constants.SUCCESS_FORWARD_NAME);
    }

    /**
     * TODO: Write sensible description for method saveProject here
     *
     * <p>
     * Updated for Online Review Update - Add Project Dropdown v1.0:
     *      Added set of 'Billing Project' property.
     * </p>
     *
     * <p>
     * Updated for Configurable Contest Terms Release Assembly v1.0:
     *      Added Project Role User Terms Of Use association generation
     * </p>
     *
     * @return TODO: Write sensible description of return value for method saveProject
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the http request.
     * @param response
     *            the http response.
     * @throws BaseException
     */
    public ActionForward saveProject(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws BaseException {

        LoggingHelper.logAction(request);
        // Cast the form to its actual type
        LazyValidatorForm lazyForm = (LazyValidatorForm) form;

        // Check whether user is creating new project or editing existing one
        final boolean newProject = (lazyForm.get("pid") == null);
        Project project = null;

        // Check if the user has the permission to perform this action
        if (newProject) {
            // Gather the roles the user has for current request
            AuthorizationHelper.gatherUserRoles(request);

            if (!AuthorizationHelper.hasUserPermission(request, Constants.CREATE_PROJECT_PERM_NAME)) {
                // If he doesn't, redirect the request to login page or report about the lack of permissions
                return ActionsHelper.produceErrorReport(mapping, getResources(request), request,
                        Constants.CREATE_PROJECT_PERM_NAME, "Error.NoPermission", Boolean.TRUE);
            }

            // At this point, redirect-after-login attribute should be removed (if it exists)
            AuthorizationHelper.removeLoginRedirect(request);
        } else {
            // Verify that certain requirements are met before processing with the Action
            CorrectnessCheckResult verification = ActionsHelper.checkForCorrectProjectId(
                    mapping, getResources(request), request, Constants.EDIT_PROJECT_DETAILS_PERM_NAME, true);
            // If any error has occurred, return action forward contained in the result bean
            if (!verification.isSuccessful()) {
                return verification.getForward();
            }

            project = verification.getProject();
        }

        // Obtain an instance of Project Manager
        ProjectManager manager = ActionsHelper.createProjectManager(request);
        // Retrieve project types, categories and statuses
        ProjectCategory[] projectCategories = manager.getAllProjectCategories();
        ProjectStatus[] projectStatuses = manager.getAllProjectStatuses();

        // This variable determines whether status of the project has been changed by this save
        // operation. This is useful to determine whether Explanation is a required field or not
        boolean statusHasChanged = false;
        boolean categoryChanged = false;
        if (newProject) {
            // Find "Active" project status
            ProjectStatus activeStatus = ActionsHelper.findProjectStatusByName(projectStatuses, "Active");
            // Find the project category by the specified id
            ProjectCategory category = ActionsHelper.findProjectCategoryById(projectCategories,
                    ((Long) lazyForm.get("project_category")).longValue());
            if (category.getProjectType().isGeneric()) {
                return ActionsHelper.produceErrorReport(mapping, getResources(request), request,
                        Constants.CREATE_PROJECT_PERM_NAME, "Error.GenericProjectType", Boolean.TRUE);
            }
            // Create Project instance
            project = new Project(category, activeStatus);

            project.setProperty("Approval Required", "true"); // All new projects by default need Approval phase.
            project.setProperty("Send Winner Emails", "true");
            statusHasChanged = true; // Status is always considered to be changed for new projects
        } else {
            long newCategoryId = ((Long) lazyForm.get("project_category")).longValue();
            String oldStatusName = project.getProjectStatus().getName();
            if (project.getProjectCategory().getId() != newCategoryId) {
                categoryChanged = true;
            }
            // Sets Project category
            ProjectCategory projectCategory = ActionsHelper.findProjectCategoryById(projectCategories, newCategoryId);
            if (projectCategory.getProjectType().isGeneric()) {
                return ActionsHelper.produceErrorReport(mapping, getResources(request), request,
                        Constants.CREATE_PROJECT_PERM_NAME, "Error.GenericProjectType", Boolean.TRUE);
            }
            project.setProjectCategory(projectCategory);

        }

        /*
         * Populate the properties of the project
         */

        // Populate project name
        project.setProperty("Project Name", lazyForm.get("project_name"));
        if (newProject) {
            // Populate project version (always set to 1.0)
            // TODO: Fix the version of the project
            project.setProperty("Project Version", "1.0");
            // Populate project root catalog id
            // OrChange - If the project category is Studio set the property to allow multiple submissions.
            if (ActionsHelper.isStudioProject(project)) {
                //TODO retrieve it from the configuration
                log.debug("setting 'Root Catalog ID' to 26887152");
                project.setProperty("Root Catalog ID", "26887152");
                log.debug("Allowing multiple submissions for this project.");
                project.setProperty("Allow multiple submissions", true);
            } else {
                project.setProperty("Root Catalog ID", ActionsHelper.getRootCategoryIdByComponentId(lazyForm.get("component_id")));
            }
            // Populate contest indicator flag
            project.setProperty("Contest Indicator", "On");
        } else {
            ProjectStatus newProjectStatus =
                ActionsHelper.findProjectStatusById(projectStatuses, ((Long) lazyForm.get("status")).longValue());
            String oldStatusName = project.getProjectStatus().getName();
            String newStatusName = (newProjectStatus != null) ? newProjectStatus.getName() : oldStatusName;

            // Determine if status has changed
            statusHasChanged = !oldStatusName.equalsIgnoreCase(newStatusName);
            // If status has changed, update the project

            // OrChange - Do not update if the project type is studio
            if (statusHasChanged) {
                // Populate project status
                project.setProjectStatus(newProjectStatus);

                // Set Completion Timestamp once the status is changed to completed, Cancelled - *, or Deleted
                ActionsHelper.setProjectCompletionDate(project, newProjectStatus, (Format) request.getAttribute("date_format"));
            }
        }

        // Populate project forum id
        project.setProperty("Developer Forum ID", lazyForm.get("forum_id"));
        // Populate project component id
        project.setProperty("Component ID", lazyForm.get("component_id"));
        // Populate project External Reference ID
        project.setProperty("External Reference ID", lazyForm.get("external_reference_id"));
        // Populate project price
        project.setProperty("Payments", lazyForm.get("payments"));
        // Populate project dr points
        Double drPoints = (Double)lazyForm.get("dr_points");
        project.setProperty("DR points", drPoints.equals(0d) ? null : drPoints);
        // Populate project SVN module
        project.setProperty("SVN Module", lazyForm.get("SVN_module"));

        if (newProject && lazyForm.get("external_reference_id") != null) {
            // Retrieve and populate version
            project.setProperty("Version ID",
                    ActionsHelper.getVersionUsingComponentVersionId(
                    ((Long) lazyForm.get("external_reference_id")).longValue()));
        }

        // Extract project's properties from the form
        Boolean autopilotOnObj = (Boolean) lazyForm.get("autopilot");
        Boolean sendEmailNotificationsObj = (Boolean) lazyForm.get("email_notifications");
        Boolean sendTLChangeNotificationsObj = (Boolean) lazyForm.get("timeline_notifications");
        Boolean digitalRunFlagObj = (Boolean) lazyForm.get("digital_run_flag");
        Boolean doNotRateProjectObj = (Boolean) lazyForm.get("no_rate_project");

        // Unbox the properties
        boolean autopilotOn = (autopilotOnObj != null) ? autopilotOnObj.booleanValue() : false;
        boolean sendEmailNotifications =
            (sendEmailNotificationsObj != null) ? sendEmailNotificationsObj.booleanValue() : false;
        boolean sendTLChangeNotifications =
            (sendTLChangeNotificationsObj != null) ? sendTLChangeNotificationsObj.booleanValue() : false;
        boolean digitalRunFlag = (digitalRunFlagObj != null) ? digitalRunFlagObj.booleanValue() : false;
        boolean doNotRateProject = (doNotRateProjectObj != null) ? doNotRateProjectObj.booleanValue() : false;

        // Populate project autopilot option
        project.setProperty("Autopilot Option", (autopilotOn) ? "On" : "Off");
        // Populate project status notifications option
        project.setProperty("Status Notification", (sendEmailNotifications) ? "On" : "Off");
        // Populate project timeline notifications option
        project.setProperty("Timeline Notification", (sendTLChangeNotifications) ? "On" : "Off");
        // Populate project Digital Run option
        project.setProperty("Digital Run Flag", (digitalRunFlag) ? "On" : "Off");
        // Populate project rated option, note that it is inverted
        project.setProperty("Rated", (doNotRateProject) ? "No" : "Yes");

        // Populate project notes
        project.setProperty("Notes", lazyForm.get("notes"));

        // since Online Review Update - Add Project Dropdown v1.0
        // Populate project notes
        if (AuthorizationHelper.hasUserRole(request, Constants.MANAGER_ROLE_NAME)
                 || AuthorizationHelper.hasUserRole(request, Constants.GLOBAL_MANAGER_ROLE_NAME)) {
                project.setProperty("Billing Project", lazyForm.get("billing_project"));
        }
        

        // TODO: Project status change, includes additional explanation to be concatenated

        // Create the map to store the mapping from phase JS ids to phases
        Map<Object, Phase> phasesJsMap = new HashMap<Object, Phase>();

        // Create the list to store the phases to be deleted
        List<Phase> phasesToDelete = new ArrayList<Phase>();

        // Save the project phases
        // FIXME: the project itself is also saved by the following call. Needs to be refactored
        Phase[] projectPhases =
            saveProjectPhases(newProject, request, lazyForm, project, phasesJsMap, phasesToDelete, statusHasChanged);


        if (newProject || categoryChanged) {
            // generate new project role terms of use associations for the recently created project.
            try {
                generateProjectRoleTermsOfUseAssociations(project.getId(),
                        project.getProjectCategory().getId(), categoryChanged);
            } catch (NamingException ne) {
                throw new BaseException(ne);
            } catch (RemoteException re) {
                throw new BaseException(re);
            } catch (CreateException ce) {
                throw new BaseException(ce);
            } catch (EJBException e) {
                throw new BaseException(e);
            }
        }

        // FIXME: resources must be saved even if there are validation errors to validate resources
        if (!ActionsHelper.isErrorsPresent(request)) {
            // Save the project resources
            saveResources(newProject, request, lazyForm, project, projectPhases, phasesJsMap);
        }

        if (!ActionsHelper.isErrorsPresent(request)) {
            // Delete the phases to be deleted
            deletePhases(request, project, phasesToDelete);
        }

        // If needed switch project current phase
        if (!newProject && !ActionsHelper.isErrorsPresent(request)) {
            switchProjectPhase(request, lazyForm, projectPhases, phasesJsMap);
        }

        // Check if there are any validation errors and return appropriate forward
        if (ActionsHelper.isErrorsPresent(request)) {
            // TODO: Check if the form is really for new project
            request.setAttribute("newProject", Boolean.valueOf(newProject));

            // Load the lookup data
            loadProjectEditLookups(request);
            if (!newProject) {
                // Store project statuses in the request
                request.setAttribute("projectStatuses", projectStatuses);
                // Store the retrieved project in the request
                request.setAttribute("project", project);
            }

            return mapping.getInputForward();
        }

        // Return success forward
        return ActionsHelper.cloneForwardAndAppendToPath(
                mapping.findForward(Constants.SUCCESS_FORWARD_NAME),"&pid=" + project.getId());
    }

    /**
     * Private helper method to generate default Project Role Terms of Use associations for a given project.
     *
     * @param projectId the project id for the associations
     * @param projectTypeId the project type id of the provided project id
     * @throws NamingException if any errors occur during EJB lookup
     * @throws RemoteException if any errors occur during EJB remote invocation
     * @throws CreateException if any errors occur during EJB creation
     * @throws EJBException if any other errors occur while invoking EJB services
     *
     * @since 1.1
     */
    private void generateProjectRoleTermsOfUseAssociations(long projectId, long projectTypeId, boolean categoryChanged)
        throws NamingException, RemoteException, CreateException, EJBException {

        ProjectRoleTermsOfUse projectRoleTermsOfUse = ProjectRoleTermsOfUseLocator.getService();

        if (categoryChanged) {
            projectRoleTermsOfUse.removeAllProjectRoleTermsOfUse(new Long(projectId).intValue(),
                    DBMS.COMMON_OLTP_DATASOURCE_NAME);
        }

        // get configurations to create the associations
        int submitterRoleId = ConfigHelper.getSubmitterRoleId();
        long submitterTermsId = ConfigHelper.getSubmitterTermsId();
        long reviewerTermsId = ConfigHelper.getReviewerTermsId();

        // create ProjectRoleTermsOfUse default associations
        projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                submitterRoleId, submitterTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);

        if (projectTypeId == DEVELOPMENT_PROJECT_TYPE_ID) {
            // if it's a development project there are several reviewer roles

            int accuracyReviewerRoleId = ConfigHelper.getAccuracyReviewerRoleId();
            int failureReviewerRoleId = ConfigHelper.getFailureReviewerRoleId();
            int stressReviewerRoleId = ConfigHelper.getStressReviewerRoleId();

            projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                    accuracyReviewerRoleId, reviewerTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);

            projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                    failureReviewerRoleId, reviewerTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);

            projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                    stressReviewerRoleId, reviewerTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);
        } else {
            // if it's not development there is a single reviewer role

            int reviewerRoleId = ConfigHelper.getReviewerRoleId();

            projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                    reviewerRoleId, reviewerTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);
        }

        // also add terms for the rest of the reviewer roles
        int primaryScreenerRoleId = ConfigHelper.getPrimaryScreenerRoleId();
        int aggregatorRoleId = ConfigHelper.getAggregatorRoleId();
        int finalReviewerRoleId = ConfigHelper.getFinalReviewerRoleId();

        projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                primaryScreenerRoleId, reviewerTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);
        projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                aggregatorRoleId, reviewerTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);
        projectRoleTermsOfUse.createProjectRoleTermsOfUse(new Long(projectId).intValue(),
                finalReviewerRoleId, reviewerTermsId, DEFAULT_TERMS_SORT_ORDER, DBMS.COMMON_OLTP_DATASOURCE_NAME);
    }


    /**
     * TODO: Document it
     *
     * @param request
     * @param project
     * @param phasesToDelete
     * @throws BaseException
     */
    private void deletePhases(HttpServletRequest request, Project project, List<Phase> phasesToDelete)
            throws BaseException {

        if (phasesToDelete.isEmpty()) {
            return;
        }

        com.topcoder.project.phases.Project phProject = phasesToDelete.get(0).getProject();

        for (int i = 0; i < phasesToDelete.size(); i++) {
            phProject.removePhase(phasesToDelete.get(i));
        }

        PhaseManager phaseManager = ActionsHelper.createPhaseManager(request, false);

        phaseManager.updatePhases(phProject, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
    }


    /**
     * <p>Updates the list of phases associated with the specified project. Optionally the method accepts the list of
     * project's phases which are to be deleted.</p>
     *
     * <p>This method has the following side-effect: if the end time for <code>Final Review</code> phase for specified
     * project is extended then start times for <code>Registration</code> and <code>Submission</code> phases for
     * projects which depend on this project (directly or indirectly) are also extended by the same amount of time.
     * However nothing happens to depending projects if end time for <code>Final Review</code> phase for specified
     * project is shrinked.</p>
     *
     * @param newProject <code>true</code> if project is new project; <code>false</code> if project is existing project
     *        which is updated.
     * @param request an <code>HttpServletRequest</code> representing current incoming request from the client.
     * @param lazyForm a <code>LazyValidatorForm</code> providing the submitted form mapped to specified request.
     * @param project a <code>Project</code> providing details for project associated with the phases.
     * @param phasesJsMap a <code>Map</code> mapping phase IDs to phases.
     * @param phasesToDelete a <code>List</code> listing the existing phases for specified project which are to be
     *        deleted.
     * @return a <code>Phase</code> array listing the updated phases associated with the specified project. 
     * @throws BaseException if an unexpected error occurs.
     */
    private Phase[] saveProjectPhases(boolean newProject, HttpServletRequest request, LazyValidatorForm lazyForm,
            Project project, Map<Object, Phase> phasesJsMap, List<Phase> phasesToDelete, boolean statusHasChanged)
        throws BaseException {
        // Obtain an instance of Phase Manager
        PhaseManager phaseManager = ActionsHelper.createPhaseManager(request, false);

        com.topcoder.project.phases.Project phProject;
        if (newProject) {
            // Create new Phases Project
            // TODO: Use real values for date and workdays, not the test ones
            phProject = new com.topcoder.project.phases.Project(
                    new Date(), (new DefaultWorkdaysFactory()).createWorkdaysInstance());
        } else {
            // Retrieve the Phases Project with the id equal to the id of specified Project
            phProject = phaseManager.getPhases(project.getId());
            // Sometimes the call to the above method returns null. Guard against this situation
            if (phProject == null) {
                // TODO: Same to-do as above
                phProject = new com.topcoder.project.phases.Project(
                        new Date(), (new DefaultWorkdaysFactory()).createWorkdaysInstance());
            }
        }

        // Get the list of all previously existing phases
        Phase[] oldPhases = phProject.getAllPhases();

        // Get the list of all existing phase types
        PhaseType[] allPhaseTypes = phaseManager.getAllPhaseTypes();

        // Get the array of phase types specified for each phase
        Long[] phaseTypes = (Long[]) lazyForm.get("phase_type");

        // This will be a Map from phases to their indexes in form
        Map<Phase, Integer> phasesToForm = new HashMap<Phase, Integer>();

        // FIRST PASS
        // 0-index phase is skipped since it is a "dummy" one
        for (int i = 1; i < phaseTypes.length; i++) {
            Phase phase = null;

            // Check what is the action to be performed with the phase
            // and obtain Phase instance in appropriate way
            String phaseAction = (String) lazyForm.get("phase_action", i);
            if ("add".equals(phaseAction)) {
                // Create new phase
                // Phase duration is set to zero here, as it will be updated later anyway
                phase = new Phase(phProject, 0);
                // Add it to Phases Project
                phProject.addPhase(phase);
            }  else {
                long phaseId = ((Long) lazyForm.get("phase_id", i)).longValue();
                if (phaseId != -1) {
                    // Retrieve the phase with the specified id
                    phase = ActionsHelper.findPhaseById(oldPhases, phaseId);

                    // Clear all the pre-existing dependencies
                    phase.clearDependencies();

                    // Clear the previously set fixed start date
                    phase.setFixedStartDate(null);
                } else {
                    // -1 value as id marks the phases that were't persisted in DB yet
                    // and so should be skipped for actions other than "add"
                    continue;
                }
            }

            // If action is "delete", proceed to the next phase
            if ("delete".equals(phaseAction)) {
                continue;
            }

            // flag value indicates using end date or using duration
            boolean useDuration = ((Boolean) lazyForm.get("phase_use_duration", i)).booleanValue();

            // If phase duration is specified
            if (useDuration) {
                String duration = (String) lazyForm.get("phase_duration", i);
                String[] parts = duration.split(":");

                // the format should be hh or hh:mm
                if (parts.length < 1 || parts.length > 2) {
                    ActionsHelper.addErrorToRequest(request,
                            new ActionMessage("error.com.cronos.onlinereview.actions.editProject.InvalidDurationFormat",
                                    phase.getPhaseType().getName()));
                    break;
                }

                try {
                    // Calculate phase length taking hh part into account
                    long length = Long.parseLong(parts[0]) * 3600 * 1000;
                    if (parts.length == 2) {
                        // If mm part is present, add it to calculated length
                        length += Long.parseLong(parts[1]) * 60 * 1000;
                    }
                    // Set phase length
                    phase.setLength(length);
                } catch (NumberFormatException nfe) {
                    // the hh or mm is not valid integer
                    ActionsHelper.addErrorToRequest(request,
                            new ActionMessage("error.com.cronos.onlinereview.actions.editProject.InvalidDurationFormat",
                                    phase.getPhaseType().getName()));
                    break;
                }
            } else {
                // Length is undetermined at current pass
                phase.setLength(0);
            }

            // Put the phase to the map from phase JS ids to phases
            phasesJsMap.put(lazyForm.get("phase_js_id", i), phase);
            // Put the phase to the map from phases to the indexes of form inputs
            phasesToForm.put(phase, i);
        }

        // Minimal date will be the project start date
        Date minDate = null;

        // SECOND PASS
        for (int i = 1; i < phaseTypes.length; i++) {
            Object phaseObj = phasesJsMap.get(lazyForm.get("phase_js_id", i));
            // If phase is not found in map, it is to be deleted
            if (phaseObj == null) {
                long phaseId = ((Long) lazyForm.get("phase_id", i)).longValue();

                if (phaseId != -1) {
                    // Retrieve the phase with the specified id
                    Phase phase = ActionsHelper.findPhaseById(oldPhases, phaseId);

                    // Signal that phases are to be deleted
                    phasesToDelete.add(phase);
                }

                // Skip further processing
                continue;
            }

            Phase phase = (Phase) phaseObj;

            /*
             * Set phase properties
             */

            String phaseAction = (String) lazyForm.get("phase_action", i);

            if ("add".equals(phaseAction)) {
                // Set phase type
                phase.setPhaseType(ActionsHelper.findPhaseTypeById(allPhaseTypes, phaseTypes[i].longValue()));
                // Set phase status to "Scheduled"
                phase.setPhaseStatus(PhaseStatus.SCHEDULED);
            }

            // If phase is not started by other phase end
            if (Boolean.FALSE.equals(lazyForm.get("phase_start_by_phase", i))) {
                // Get phase start date from form
                Date phaseStartDate = parseDatetimeFormProperties(lazyForm, i, "phase_start_date",
                        "phase_start_time", "phase_start_AMPM");
                // Set phase fixed start date
                phase.setFixedStartDate(phaseStartDate);

                // Check if the current date is minimal
                if (minDate == null || phaseStartDate.getTime() < minDate.getTime()) {
                    minDate = phaseStartDate;
                }
            } else {
                // Get the dependency phase
                Phase dependencyPhase = (Phase) phasesJsMap.get(lazyForm.get("phase_start_phase", i));

                if (dependencyPhase != null) {
                    boolean dependencyStart;
                    boolean dependantStart;
                    if ("ends".equals(lazyForm.get("phase_start_when", i))) {
                        dependencyStart = false;
                        dependantStart = true;
                    } else {
                        dependencyStart = true;
                        dependantStart = true;
                    }

                    long unitMutiplier = 1000 * 3600 * ("days".equals(lazyForm.get("phase_start_dayshrs", i)) ? 24 : 1);
                    long lagTime = unitMutiplier * ((Integer) lazyForm.get("phase_start_amount", i)).longValue();

                    // Create phase Dependency
                    Dependency dependency = new Dependency(dependencyPhase, phase,
                            dependencyStart, dependantStart, lagTime);

                    // Add dependency to phase
                    phase.addDependency(dependency);
                }
            }

            /*
             *  Set phase criteria
             */
            Long scorecardId = (Long) lazyForm.get("phase_scorecard", i);
            // If the scorecard id is specified, set it
            if (scorecardId != null) {
                phase.setAttribute("Scorecard ID", scorecardId.toString());
            }
            Integer requiredRegistrations = (Integer) lazyForm.get("phase_required_registrations", i);
            // If the number of required registrations is specified, set it
            if (requiredRegistrations != null) {
                phase.setAttribute("Registration Number", requiredRegistrations.toString());
            }
            Integer requiredSubmissions = (Integer) lazyForm.get("phase_required_submissions", i);
            // If the number of required submissions is specified, set it
            if (requiredSubmissions != null) {
                phase.setAttribute("Submission Number", requiredSubmissions.toString());
            }
            // If the number of required reviewers is specified, set it
            Integer requiredReviewer = (Integer) lazyForm.get("phase_required_reviewers", i);
            if (requiredReviewer != null) {

                if (requiredReviewer < 1) {
                    ActionsHelper.addErrorToRequest(request,
                            new ActionMessage("error.com.cronos.onlinereview.actions.editProject.InvalidReviewersNumber",
                                    phase.getPhaseType().getName()));
                    break;
                }

                phase.setAttribute("Reviewer Number", requiredReviewer.toString());
            }

            Boolean manualScreening = (Boolean) lazyForm.get("phase_manual_screening", i);
            // If the manual screening flag is specified, set it
            if (manualScreening != null) {
                phase.setAttribute("Manual Screening", manualScreening.booleanValue() ? "Yes" : "No");
            } else {
                phase.setAttribute("Manual Screening", "No");
            }
            Boolean viewAppealResponses = (Boolean) lazyForm.get("phase_view_appeal_responses", i);
            // If the view appeal response during appeals flag is specified, set it
            if (viewAppealResponses != null) {
                phase.setAttribute("View Response During Appeals", viewAppealResponses.booleanValue() ? "Yes" : "No");
            }
        }

        // Update project start date if needed
        if (minDate != null) {
            phProject.setStartDate(minDate);
        }

        // THIRD PASS
        boolean hasCircularDependencies = false;
        Set<Phase> processed = new HashSet<Phase>();
        for (int i = 1; i < phaseTypes.length; i++) {
            Object phaseObj = phasesJsMap.get(lazyForm.get("phase_js_id", i));
            // If phase is not found in map, it was deleted and should not be processed
            if (phaseObj == null) {
                continue;
            }

            Phase phase = (Phase) phaseObj;

            // If phase was already processed, skip it
            if (processed.contains(phase)) {
                continue;
            }

            Set<Phase> visited = new HashSet<Phase>();
            Stack<Phase> stack = new Stack<Phase>();

            for (;;) {
                processed.add(phase);
                visited.add(phase);
                stack.push(phase);

                Dependency[] dependencies = phase.getAllDependencies();
                // Actually there should be either zero or one dependency, we'll assume it
                if (dependencies.length == 0) {
                    // If there is no dependency, stop processing
                    break;
                } else {
                    phase = dependencies[0].getDependency();
                    if (visited.contains(phase)) {
                        // There is circular dependency, report it and stop processing
                        // TODO: Report the particular phases
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.CircularDependency");
                        hasCircularDependencies = true;
                        break;
                    }
                }
            }

            while (!stack.empty()) {
                phase = (Phase) stack.pop();
                int paramIndex = ((Integer) phasesToForm.get(phase)).intValue();

                // If the phase is scheduled to start before some other phase start/end
                if (Boolean.TRUE.equals(lazyForm.get("phase_start_by_phase", paramIndex)) &&
                        "minus".equals(lazyForm.get("phase_start_plusminus", paramIndex))) {
                    Dependency dependency = phase.getAllDependencies()[0];

                    Date dependencyDate;
                    if ("ends".equals(lazyForm.get("phase_start_when", paramIndex))) {
                        dependencyDate = dependency.getDependency().getScheduledEndDate();
                    } else {
                        dependencyDate = dependency.getDependency().getScheduledStartDate();
                    }
                    phase.setFixedStartDate(new Date(dependencyDate.getTime() - dependency.getLagTime()));

                    phase.clearDependencies();
                }

                try {
                    // Set scheduled start date to calculated start date
                    phase.setScheduledStartDate(phase.calcStartDate());

                    // flag value indicates using end date or using duration
                    boolean useDuration = ((Boolean) lazyForm.get("phase_use_duration", paramIndex)).booleanValue();

                    // If phase duration was not specified
                    if (!useDuration) {
                        // Get phase end date from form
                        Date phaseEndDate = parseDatetimeFormProperties(lazyForm, paramIndex,
                                "phase_end_date", "phase_end_time", "phase_end_AMPM");

                        // Calculate phase length
                        long length = phaseEndDate.getTime() - phase.getScheduledStartDate().getTime();
                        // Check if the end date of phase goes after the start date
                        if (length < 0) {
                            ActionsHelper.addErrorToRequest(request, new ActionMessage(
                                    "error.com.cronos.onlinereview.actions.editProject.StartAfterEnd",
                                    phase.getPhaseType().getName()));
                            break;
                        }

                        // Get the workdays
                        Workdays workdays = phProject.getWorkdays();

                        // Perform binary search to take the workdays into account
                        long minLength = 0;
                        long maxLength = length;

                        Date estimatedEndDate = workdays.add(phase.getScheduledStartDate(),
                                WorkdaysUnitOfTime.MINUTES, (int) (length / 60000));
                        long diff = estimatedEndDate.getTime() - phaseEndDate.getTime();
                        while (Math.abs(diff) > 60000) {
                            if (diff < 0) {
                                // Current length is too small
                                minLength = length;
                            } else {
                                // Current length is too big
                                maxLength = length;
                            }
                            length = (minLength + maxLength) / 2;

                            estimatedEndDate = workdays.add(phase.getScheduledStartDate(),
                                    WorkdaysUnitOfTime.MINUTES, (int) (length / 60000));
                            diff = estimatedEndDate.getTime() - phaseEndDate.getTime();
                        }

                        // Set phase duration appropriately
                        phase.setLength(length);
                    }

                    // Set scheduled phase end date to calculated phase end date
                    phase.setScheduledEndDate(phase.calcEndDate());
                } catch (CyclicDependencyException e) {
                    // There is circular dependency, report it and stop processing
                    // TODO: Report the particular phases
                    ActionsHelper.addErrorToRequest(request,
                            "error.com.cronos.onlinereview.actions.editProject.CircularDependency");
                    hasCircularDependencies = true;
                    break;
                }
            }

            if (hasCircularDependencies) {
                break;
            }
        }

        if (ActionsHelper.isErrorsPresent(request)) {
            // TODO: Return null or so
            return oldPhases;
        }

        // Get all the project phases
        Phase[] projectPhases = phProject.getAllPhases();
        // Sort project phases
        Arrays.sort(projectPhases, new Comparators.ProjectPhaseComparer());

        // Validate the project phases
        boolean validationSucceeded = validateProjectPhases(request, project, projectPhases);

        // Get Explanation for edited project.
        // It does not matter what this string contains for new projects
        String explanationText = (!newProject && !statusHasChanged) ? (String) lazyForm.get("explanation") : "***";

        // Validate Explanation, but only if status has not been changed
        if (explanationText == null || explanationText.trim().length() == 0) {
            // Indicate unsuccessful validation
            validationSucceeded = false;
            // Add error that explains the validation error
            ActionsHelper.addErrorToRequest(request, "explanation",
                    "error.com.cronos.onlinereview.actions.editProject.explanation");
        }

        if (!validationSucceeded) {
            // If project validation has failed, return immediately
            return projectPhases;
        }

        // FIXME: Refactor it
        ProjectManager projectManager = ActionsHelper.createProjectManager(request);
        ProjectLinkManager projectLinkManager = ActionsHelper.createProjectLinkManager(request);

        // Set project rating date
        ActionsHelper.setProjectRatingDate(project, projectPhases, (Format) request.getAttribute("date_format"));

        if (newProject) {
            // Create project in persistence level
            projectManager.createProject(project, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));

            // Set the id of Phases Project to be equal to the id of appropriate Project
            phProject.setId(project.getId());
        } else {
            projectManager.updateProject(project, (String) lazyForm.get("explanation"),
                    Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
        }

        // Adjust the depending projects timelines if necessary
        String operator = Long.toString(AuthorizationHelper.getLoggedInUserId(request));
        ContestDependencyAutomation auto
            = new ContestDependencyAutomation(phaseManager, projectManager, projectLinkManager);
        ActionsHelper.adjustDependentProjects(phProject, phaseManager, auto, operator);

        // Save the phases at the persistence level
        phaseManager.updatePhases(phProject, operator);
        // TODO: The following line was added just to be safe. May be unneeded as well as another one
        projectPhases = phProject.getAllPhases();
        // Sort project phases
        Arrays.sort(projectPhases, new Comparators.ProjectPhaseComparer());

        return projectPhases;
    }

    /**
     *
     * TODO: Document it.
     *
     * @param request
     * @param lazyForm
     * @param projectPhases
     * @param phasesJsMap
     * @throws BaseException
     */
    private void switchProjectPhase(HttpServletRequest request, LazyValidatorForm lazyForm,
            Phase[] projectPhases, Map<Object, Phase> phasesJsMap) throws BaseException {

        // Get name of action to be performed
        String action = (String) lazyForm.get("action");

        // Get new current phase id
        String phaseJsId = (String) lazyForm.get("action_phase");

        if (phaseJsId != null && phasesJsMap.containsKey(phaseJsId)) {
            // Get the phase to be operated on
            Phase phase = (Phase) phasesJsMap.get(phaseJsId);

            // Get the status of phase
            PhaseStatus phaseStatus = phase.getPhaseStatus();
            // Get the type of the phase
            PhaseType phaseType = phase.getPhaseType();

            // Obtain an instance of Phase Manager
            PhaseManager phaseManager = ActionsHelper.createPhaseManager(request, true);

            if ("close_phase".equals(action)) {
                if (phaseStatus.getName().equals(PhaseStatus.OPEN.getName()) && phaseManager.canEnd(phase)) {
                    // Close the phase
                    phaseManager.end(phase, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
                } else {
                    ActionsHelper.addErrorToRequest(request, new ActionMessage(
                            "error.com.cronos.onlinereview.actions.editProject.CannotClosePhase", phaseType.getName()));
                }
            } else if ("open_phase".equals(action)) {
                if (phaseStatus.getName().equals(PhaseStatus.SCHEDULED.getName()) && phaseManager.canStart(phase)) {
                    // Open the phase
                    phaseManager.start(phase, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
                } else {
                    ActionsHelper.addErrorToRequest(request, new ActionMessage(
                            "error.com.cronos.onlinereview.actions.editProject.CannotOpenPhase", phaseType.getName()));
                }
            }
        }
    }

    /**
     * TODO: Document it
     * Note, that this method assumes that phases are already sorted by the start date, etc.
     *
     * @param request an <code>HttpServletRequest</code> representing incoming request from the client. 
     * @param project
     * @param projectPhases
     * @return
     */
    private boolean validateProjectPhases(HttpServletRequest request, Project project, Phase[] projectPhases) {
        boolean arePhasesValid = true;

        // TODO: Refactor this function, make it more concise
        // IF there is a Post-Mortem phase in project skip the validation as that phase may appear anywhere
        // in project timeline and actual order of the phases is not significant
        boolean postMortemPhaseExists = false;
        for (int i = 0; i < projectPhases.length; i++) {
            Phase projectPhase = projectPhases[i];
            if (projectPhase.getPhaseType().getName().equals(POST_MORTEM_PHASE_NAME)) {
                return true;
            }
        }


        // Check the beginning phase, it should be either Registration or submission
        if (projectPhases.length > 0 &&
                !projectPhases[0].getPhaseType().getName().equals(REGISTRATION_PHASE_NAME) &&
                !projectPhases[0].getPhaseType().getName().equals(SUBMISSION_PHASE_NAME) &&
                !projectPhases[0].getPhaseType().getName().equals(POST_MORTEM_PHASE_NAME)) {
            ActionsHelper.addErrorToRequest(request,
                    "error.com.cronos.onlinereview.actions.editProject.WrongBeginningPhase");
            arePhasesValid = false;
        }
        

        // Check the phases as a whole
        for (int i = 0; i < projectPhases.length; i++) {
            final String previousPhaseName = i > 0 ? projectPhases[i - 1].getPhaseType().getName() : "";
            final String currentPhaseName = projectPhases[i].getPhaseType().getName();
            if (currentPhaseName.equals(SUBMISSION_PHASE_NAME)) {
                // Submission should follow registration or post-mortem if it exists
                if (i > 0 && !previousPhaseName.equals(REGISTRATION_PHASE_NAME)
                          && !postMortemPhaseExists) {
                    ActionsHelper.addErrorToRequest(request,
                            "error.com.cronos.onlinereview.actions.editProject.SubmissionMustFollow");
                    arePhasesValid = false;
                }
            } else {
                final String nextPhaseName = i < (projectPhases.length - 1) ? projectPhases[i + 1].getPhaseType().getName() : "";
                if (currentPhaseName.equals(REGISTRATION_PHASE_NAME)) {
                    // Registration should be followed by submission or post-mortem
                    if (i == projectPhases.length - 1
                            || !nextPhaseName.equals(SUBMISSION_PHASE_NAME)
                            && !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.RegistrationMustBeFollowed");
                        arePhasesValid = false;
                    }
                } else if (currentPhaseName.equals(REVIEW_PHASE_NAME)) {
                    // Review should follow submission or screening or post-mortem
                    if (i == 0 || (!previousPhaseName.equals(SUBMISSION_PHASE_NAME) &&
                            !previousPhaseName.equals(SCREENING_PHASE_NAME)
                            && !postMortemPhaseExists)) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.ReviewMustFollow");
                        arePhasesValid = false;
                    }
                } else if (currentPhaseName.equals(APPEALS_PHASE_NAME)) {
                    // Appeals should follow review
                    if (i == 0 || !previousPhaseName.equals(REVIEW_PHASE_NAME) &&
                                  !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.AppealsMustFollow");
                        arePhasesValid = false;
                    }
                    // Appeals should be followed by the appeals response
                    if (i == projectPhases.length - 1 ||
                            !nextPhaseName.equals(APPEALS_RESPONSE_PHASE_NAME) &&
                            !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.AppealsMustBeFollowed");
                        arePhasesValid = false;
                    }
                } else if (currentPhaseName.equals(APPEALS_RESPONSE_PHASE_NAME)) {
                    // Appeal response should follow appeals
                    if (i == 0 || !previousPhaseName.equals(APPEALS_PHASE_NAME) &&
                                  !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.AppealsResponseMustFollow");
                        arePhasesValid = false;
                    }
                } else if (currentPhaseName.equals(AGGREGATION_PHASE_NAME)) {
                    // Aggregation should follow appeals response or review, or aggregation review or post-mortem
                    if (i == 0 ||
                            (!previousPhaseName.equals(APPEALS_RESPONSE_PHASE_NAME) &&
                            !previousPhaseName.equals(REVIEW_PHASE_NAME) &&
                            !previousPhaseName.equals(AGGREGATION_REVIEW_PHASE_NAME) &&
                            !postMortemPhaseExists)) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.AggregationMustFollow");
                        arePhasesValid = false;
                    }
                    // Aggregation should be followed by the aggregation review
                    if (i == projectPhases.length - 1 ||
                            !nextPhaseName.equals(AGGREGATION_REVIEW_PHASE_NAME) &&
                            !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.AggregationMustBeFollowed");
                        arePhasesValid = false;
                    }
                } else if (currentPhaseName.equals(AGGREGATION_REVIEW_PHASE_NAME)) {
                    // Aggregation review should follow aggregation
                    if (i == 0 ||
                            !previousPhaseName.equals(AGGREGATION_PHASE_NAME) &&
                            !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.AggregationReviewMustFollow");
                        arePhasesValid = false;
                    }
                } else if (currentPhaseName.equals(FINAL_FIX_PHASE_NAME)) {
                    // Final fix should follow either appeals response or aggregation review, or final review
                    if (i == 0 ||
                            (!previousPhaseName.equals(APPEALS_RESPONSE_PHASE_NAME) &&
                            !previousPhaseName.equals(AGGREGATION_REVIEW_PHASE_NAME) &&
                            !previousPhaseName.equals(APPROVAL_PHASE_NAME) &&
                            !postMortemPhaseExists &&
                            !previousPhaseName.equals(FINAL_REVIEW_PHASE_NAME))) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.FinalFixMustFollow");
                        arePhasesValid = false;
                    }
                    // Final fix should be followed by the final review
                    if (i == projectPhases.length - 1 ||
                            !nextPhaseName.equals(FINAL_REVIEW_PHASE_NAME) &&
                            !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.FinalFixMustBeFollowed");
                        arePhasesValid = false;
                    }
                }  else if (currentPhaseName.equals(FINAL_REVIEW_PHASE_NAME)) {
                    // Final review should follow final fix
                    if (i == 0 ||
                            !previousPhaseName.equals(FINAL_FIX_PHASE_NAME)
                            && !postMortemPhaseExists) {
                        ActionsHelper.addErrorToRequest(request,
                                "error.com.cronos.onlinereview.actions.editProject.FinalReviewMustFollow");
                        arePhasesValid = false;
                    }
                }
            }
        }

        return arePhasesValid;
    }

    /**
     * TODO: Document it
     *
     * @param lazyForm
     * @param dateProperty
     * @param timeProperty
     * @param ampmProperty
     * @return
     */
    private Date parseDatetimeFormProperties(LazyValidatorForm lazyForm, int propertyIndex, String dateProperty,
            String timeProperty, String ampmProperty) {
        // Retrieve the values of form properties
        String dateString = (String) lazyForm.get(dateProperty, propertyIndex);
        String timeString = (String) lazyForm.get(timeProperty, propertyIndex);
        String ampmString = (String) lazyForm.get(ampmProperty, propertyIndex);

        // Obtain calendar instance to be used to create Date instance
        Calendar calendar = Calendar.getInstance();

        // Parse date string
        String[] dateParts = dateString.trim().split("[./:-]|([ ])+");
        calendar.set(Calendar.YEAR, Integer.parseInt(dateParts[2]) + (dateParts[2].length() > 2  ? 0 : 2000));
        calendar.set(Calendar.MONTH, Integer.parseInt(dateParts[0]) - 1);
        calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateParts[1]));

        // Parse time string
        String[] timeParts = timeString.trim().split("[./:-]|([ ])+");
        int hour = Integer.parseInt(timeParts[0]);
        calendar.set(Calendar.HOUR, hour != 12 ? hour : 0);
        if (timeParts.length == 1) {
            calendar.set(Calendar.MINUTE, 0);
        } else {
            calendar.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
        }

        // Set am/pm property
        calendar.set(Calendar.AM_PM, "am".equals(ampmString) ? Calendar.AM : Calendar.PM);

        // Set seconds, milliseconds
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Returned parsed Date
        return calendar.getTime();
    }

    /**
     * Private helper method to save resources
     *
     * <p>
     *     Updated for Configurable Contest Terms Release Assembly v1.0:
     *     Added Project Role User Terms Of Use verification when adding/updating project resources
     * </p>
     *
     * <p>
     *     Updated for Appeals Early Completion Release Assembly 1.0:
     *     Added Appeals Completed Early flag manipulation when project is saved
     * </p>
     *
     * @param newProject true if a new project is being saved
     * @param request the HttpServletRequest
     * @param lazyForm the form
     * @param project the project being saved
     * @param projectPhases the project phases being saved
     * @param phasesJsMap the phasesJsMap
     * @throws BaseException if any error occurs
     */
    private void saveResources(boolean newProject, HttpServletRequest request, LazyValidatorForm lazyForm,
            Project project, Phase[] projectPhases, Map<Object, Phase> phasesJsMap) throws BaseException {

        // Obtain the instance of the User Retrieval
        UserRetrieval userRetrieval = ActionsHelper.createUserRetrieval(request);

        // Obtain the instance of the Resource Manager
        ResourceManager resourceManager = ActionsHelper.createResourceManager(request);

        // Get all types of resource roles
        ResourceRole[] resourceRoles = resourceManager.getAllResourceRoles();

        // Get all types of notifications
        NotificationType[] types = resourceManager.getAllNotificationTypes();
        long timelineNotificationId = Long.MIN_VALUE;

        // get the id for the timelineNotification
        for (int i = 0; i < types.length; ++i) {
            if (types[i].getName().equals("Timeline Notification")) {
                timelineNotificationId = types[i].getId();
                break;
            }
        }

        // need to do the check timelineNotifictionId exists here
        if (timelineNotificationId == Long.MIN_VALUE) {
            ActionsHelper.addErrorToRequest(request,
            "error.com.cronos.onlinereview.actions.editProject.TimelineNotification.NotFound");
            return;
        }

        // Get the array of resource names
        String[] resourceNames = (String[]) lazyForm.get("resources_name");

        // HashSet used to identify resource of new user
        Set<Long> newUsers = new HashSet<Long>();
        Set<Long> newModerators = new HashSet<Long>();
        Set<Long> oldUsers = new HashSet<Long>();
        Set<Long> deletedUsers = new HashSet<Long>();
        Set<Long> newSubmitters = new HashSet<Long>();
        Set<Long> newUsersForumWatch = new HashSet<Long>();

        // 0-index resource is skipped as it is a "dummy" one
        boolean allResourcesValid=true;
        for (int i = 1; i < resourceNames.length; i++) {

            if (resourceNames[i] == null || resourceNames[i].trim().length() == 0) {
                ActionsHelper.addErrorToRequest(request, "resources_name[" + i + "]",
                        "error.com.cronos.onlinereview.actions.editProject.Resource.Empty");
                allResourcesValid=false;
                continue;
            }

            // Get info about user with the specified handle
            ExternalUser user = userRetrieval.retrieveUser(resourceNames[i]);

            // If there is no user with such handle, indicate an error
            if (user == null) {
                ActionsHelper.addErrorToRequest(request, "resources_name[" + i + "]",
                        "error.com.cronos.onlinereview.actions.editProject.Resource.NotFound");
                allResourcesValid=false;
            }
        }

        // validate resources have correct terms of use
        try {
            allResourcesValid = allResourcesValid && validateResourceTermsOfUse(request, lazyForm, project, userRetrieval, resourceNames);
            allResourcesValid = allResourcesValid && validateResourceEligibility(request, lazyForm, project, userRetrieval, resourceNames);
        } catch (NamingException ne) {
            throw new BaseException(ne);
        } catch (RemoteException re) {
            throw new BaseException(re);
        } catch (CreateException ce) {
            throw new BaseException(ce);
        } catch (EJBException e) {
            throw new BaseException(e);
        } catch (ContestEligibilityValidatorException e) {
            throw new BaseException(e);
        }

        // No resources are updated if at least one of them is incorrect.
        if (!allResourcesValid)
            return;

        // BUGR-2807: A map mapping the IDs for Submitters to their respective payments. Payment may be NULL
        Map<Long, Double> submitterPayments = new HashMap<Long, Double>();

        // 0-index resource is skipped as it is a "dummy" one
        for (int i = 1; i < resourceNames.length; i++) {

            // Get info about user with the specified handle
            ExternalUser user = userRetrieval.retrieveUser(resourceNames[i]);

            Resource resource;
            
            // BUGR-2807: Parse resource payment
            Double resourcePayment = null;
            if (Boolean.TRUE.equals(lazyForm.get("resources_payment", i))) {
                resourcePayment = (Double) lazyForm.get("resources_payment_amount", i);
            }

            // Check what is the action to be performed with the resource
            // and obtain Resource instance in appropriate way
            String resourceAction = (String) lazyForm.get("resources_action", i);
            if ("add".equals(resourceAction)) {
                // Create new resource
                resource = new Resource();

                // FIXME: Format this date properly.
                resource.setProperty("Registration Date", DATE_FORMAT.format(new Date()));

                newUsers.add(user.getId());

                //System.out.println("ADD:" + user.getId());
            }  else {
                Long resourceId = (Long) lazyForm.get("resources_id", i);

                if (resourceId.longValue() != -1) {
                    // Retrieve the resource with the specified id
                    resource = resourceManager.getResource(resourceId.longValue());
                    oldUsers.add(user.getId());
                    //System.out.println("REMOVE:" + user.getId());
                } else {
                    // -1 value as id marks the resources that were't persisted in DB yet
                    // and so should be skipped for actions other then "add"
                    oldUsers.add(user.getId());
                    //System.out.println("REMOVE:" + user.getId());
                    continue;
                }
            }

            // If action is "delete", delete the resource and proceed to the next one
            if ("delete".equals(resourceAction)) {
                deletedUsers.add(user.getId());
                // delete project_result
                ActionsHelper.deleteProjectResult(project, user.getId(),
                        ((Long) lazyForm.get("resources_role", i)).longValue());
                resourceManager.removeResource(resource,
                        Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
                resourceManager.removeNotifications(new long[] {user.getId()}, project.getId(),
                        timelineNotificationId, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
                continue;
            }

            // Set resource properties
            resource.setProject(new Long(project.getId()));
            resource.setProperty("Payment", resourcePayment);
            resource.setProperty("Payment Status", lazyForm.get("resources_paid", i));

            boolean resourceRoleChanged = false;
            ResourceRole role = ActionsHelper.findResourceRoleById(
                    resourceRoles, ((Long) lazyForm.get("resources_role", i)).longValue());
            if (role != null && resource.getResourceRole() != null &&
                role.getId() != resource.getResourceRole().getId()) {
                // delete project_result if old role is submitter
                // populate project_result if new role is submitter and project is component
                ActionsHelper.changeResourceRole(project, user.getId(), resource.getResourceRole().getId(),
                    role.getId());

                resourceRoleChanged = true;
            }
            resource.setResourceRole(role);

            // BUGR-2807: For submitters collect the payments to be updated in project_result table later
            if (isSubmitter(resource)) {
                submitterPayments.put(user.getId(), resourcePayment);
            }

            resource.setProperty("Handle", resourceNames[i]);

            // Set resource phase id, if needed
            Long phaseTypeId = resource.getResourceRole().getPhaseType();
            if (phaseTypeId != null) {
                Phase phase = phasesJsMap.get(lazyForm.get("resources_phase", i));
                if (phase != null) {
                    resource.setPhase(phase.getId());
                } else {
                    // TODO: Probably issue validation error here
                }
            }

            // Set resource properties copied from external user
            resource.setProperty("External Reference ID", new Long(user.getId()));
            // not store in resource info resource.setProperty("Email", user.getEmail());

            String resourceRole = resource.getResourceRole().getName();
            // If resource is a submitter, we need to store appropriate rating and reliability
            // Note, that it is done only in the case resource is added or resource role is changed
            if (resourceRole.equals("Submitter") && (resourceRoleChanged || resourceAction.equals("add"))) {
                if (project.getProjectCategory().getName().equals("Design")) {
                    resource.setProperty("Rating", user.getDesignRating());
                    resource.setProperty("Reliability", user.getDesignReliability());
                } else if (project.getProjectCategory().getName().equals("Development")) {
                    resource.setProperty("Rating", user.getDevRating());
                    resource.setProperty("Reliability", user.getDevReliability());
                }

                // add "Appeals Completed Early" flag.
                resource.setProperty(Constants.APPEALS_COMPLETED_EARLY_PROPERTY_KEY, Constants.NO_VALUE);
            }

            if ("add".equals(resourceAction)) {

                if (resourceRole.equals("Manager") || resourceRole.equals("Observer") 
                         || resourceRole.equals("Designer")  || resourceRole.equals("Client Manager")  || resourceRole.equals("Copilot"))
                {   
                    // no need for Applications/Components/LCSUPPORT
                    if (!resource.getProperty("Handle").equals("Applications") &&
                        !resource.getProperty("Handle").equals("Components") &&
                        !resource.getProperty("Handle").equals("LCSUPPORT"))
                    {
                        newUsersForumWatch.add(user.getId());
                    }
                    
                }
            }

            // client manager and copilot have moderator role
            if (resourceRole.equals("Client Manager")  || resourceRole.equals("Copilot")
                    || resourceRole.equals("Observer") || resourceRole.equals("Designer"))
            {   
                newUsers.remove(user.getId());
                newModerators.add(user.getId());
                
            }

            // make sure "Appeals Completed Early" flag is not set if the role is not submitter.
            if (resourceRoleChanged && !resourceRole.equals(Constants.SUBMITTER_ROLE_NAME)) {
                resource.setProperty(Constants.APPEALS_COMPLETED_EARLY_PROPERTY_KEY, null);
            }

            // Save the resource in the persistence level
            resourceManager.updateResource(resource, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));

            if ("add".equals(resourceAction) && resourceRole.equals("Submitter")) {
                newSubmitters.add(user.getId());
            }
        }

        for (Long id : oldUsers) {
            newUsers.remove(id);
            newSubmitters.remove(id);
        }

        // Populate project_result and component_inquiry for new submitters
        ActionsHelper.populateProjectResult(project, newSubmitters);

        // BUGR-2807: Update project_result.payment for submitters
        ActionsHelper.updateSubmitterPayments(project.getId(), submitterPayments);

        // Update all the timeline notifications
        if (project.getProperty("Timeline Notification").equals("On") && !newUsers.isEmpty()) {
            // Remove duplicated user ids
            long[] existUserIds = resourceManager.getNotifications(project.getId(), timelineNotificationId);
            Set<Long> finalUsers = new HashSet<Long>(newUsers);

            for (int i = 0; i < existUserIds.length; i++) {
                finalUsers.remove(existUserIds[i]);
            }

            long[] userIds = new long[finalUsers.size()];
            int i = 0;
            for (Long id : finalUsers) {
                userIds[i++] = id;
            }

            resourceManager.addNotifications(userIds, project.getId(),
                    timelineNotificationId, Long.toString(AuthorizationHelper.getLoggedInUserId(request)));
        }

        // Update rboard_application table with the reviewers set in the resources.
        ActionsHelper.synchronizeRBoardApplications(project);

        // Add forum permissions for all new users and remove permissions for removed resources.
        ActionsHelper.removeForumPermissions(project, deletedUsers);
        ActionsHelper.addForumPermissions(project, newUsers, false);
        ActionsHelper.addForumPermissions(project, newModerators, true);

        long forumId = 0;
        if (project.getProperty("Developer Forum ID") != null 
              && ((Long)project.getProperty("Developer Forum ID")).longValue() != 0)
        {
            forumId = ((Long)project.getProperty("Developer Forum ID")).longValue();
        }

        ActionsHelper.removeForumWatch(project, deletedUsers, forumId);
        ActionsHelper.addForumWatch(project, newUsersForumWatch, forumId);
    }

    /**
     * Helper method to validate if resources in the request have the required terms of use
     *
     * @param request the current <code>HttpServletRequest</code>.
     * @param lazyForm the edition <code>LazyValidatorForm</code>.
     * @param project the edited <code>Project</code>.
     * @param userRetrieval a <code>UserRetrieval</code> instance to obtain the user id.
     * @param resourceNames a <code>String[]</code> containing edited resource names.
     *
     * @throws NamingException if any errors occur during EJB lookup
     * @throws RemoteException if any errors occur during EJB remote invocation
     * @throws CreateException if any errors occur during EJB creation
     * @throws EJBException if any other errors occur while invoking EJB services
     * @throws BaseException if any other errors occur while retrieving user
     *
     * @return true if all resources are valid
     * @since 1.1
     */
    private boolean validateResourceTermsOfUse(HttpServletRequest request, LazyValidatorForm lazyForm,
            Project project, UserRetrieval userRetrieval, String[] resourceNames)
            throws NamingException, RemoteException, CreateException, EJBException, BaseException {

        boolean allResourcesValid = true;

        // get remote services
        ProjectRoleTermsOfUse projectRoleTermsOfUse = ProjectRoleTermsOfUseLocator.getService();
        UserTermsOfUse userTermsOfUse = UserTermsOfUseLocator.getService();
        TermsOfUse termsOfUse = TermsOfUseLocator.getService();

        // validate that new resources have agreed to the necessary terms of use
        // 0-index resource is skipped as it is a "dummy" one
        for (int i = 1; i < resourceNames.length; i++) {
            if (resourceNames[i] != null && resourceNames[i].trim().length() > 0) {
                ExternalUser user = userRetrieval.retrieveUser(resourceNames[i]);
                String resourceAction = (String) lazyForm.get("resources_action", i);
                // check for additions or modifications
                if (!"delete".equals(resourceAction)) {
                    long roleId = ((Long) lazyForm.get("resources_role", i)).longValue();
                    long userId = user.getId();

                    List<Long>[] necessaryTerms = projectRoleTermsOfUse.getTermsOfUse(new Long(project.getId()).intValue(),
                            new int[] {new Long(roleId).intValue()}, DBMS.COMMON_OLTP_DATASOURCE_NAME);

                    for (int j = 0; j < necessaryTerms.length; j++) {
                        if (necessaryTerms[j] != null) {
                            for (Long termsId : necessaryTerms[j]) {
                                // check if the user has this terms
                                if (!userTermsOfUse.hasTermsOfUse(userId, termsId, DBMS.COMMON_OLTP_DATASOURCE_NAME)) {
                                    // get missing terms of use title
                                    TermsOfUseEntity terms =  termsOfUse.getEntity(termsId, DBMS.COMMON_OLTP_DATASOURCE_NAME);

                                    // add the error
                                    ActionsHelper.addErrorToRequest(request, "resources_name[" + i + "]",
                                        new ActionMessage("error.com.cronos.onlinereview.actions.editProject.Resource.MissingTerms",
                                        terms.getTitle()));

                                    allResourcesValid=false;
                                }
                            }
                        }
                    }
                }
            }
        }

        return allResourcesValid;
    }

    /**
     * Helper method to validate if resources in the request are eligible for the project
     *
     * @param request the current <code>HttpServletRequest</code>.
     * @param lazyForm the edition <code>LazyValidatorForm</code>.
     * @param project the edited <code>Project</code>.
     * @param userRetrieval a <code>UserRetrieval</code> instance to obtain the user id.
     * @param resourceNames a <code>String[]</code> containing edited resource names.
     *
     * @throws NamingException if any errors occur during EJB lookup
     * @throws RemoteException if any errors occur during EJB remote invocation
     * @throws CreateException if any errors occur during EJB creation
     * @throws EJBException if any other errors occur while invoking EJB services
     * @throws BaseException if any other errors occur while retrieving user
     *
     * @return true if all resources are valid
     * @since 1.1
     */
    private boolean validateResourceEligibility(HttpServletRequest request, LazyValidatorForm lazyForm,
            Project project, UserRetrieval userRetrieval, String[] resourceNames)
            throws NamingException, RemoteException, CreateException, 
                   EJBException, BaseException, ContestEligibilityValidatorException {

        boolean allResourcesValid = true;


        // validate that new resources have agreed to the necessary terms of use
        // 0-index resource is skipped as it is a "dummy" one
        for (int i = 1; i < resourceNames.length; i++) {
            if (resourceNames[i] != null && resourceNames[i].trim().length() > 0) {
                ExternalUser user = userRetrieval.retrieveUser(resourceNames[i]);
                String resourceAction = (String) lazyForm.get("resources_action", i);
                // check for additions or modifications
                if (!"delete".equals(resourceAction)) {
                    long userId = user.getId();

                    // dont check Applications or Components
                    if (resourceNames[i].equals("Applications") ||
                        resourceNames[i].equals("Components") ||
                        resourceNames[i].equals("LCSUPPORT"))
                    {
                        continue;
                    }
                        
                    // dont check project creator
                    if (project.getCreationUser().equals(Long.toString(userId)))
                    {
                        continue;
                    }

                    if (!ContestEligibilityServiceLocator.getServices().isEligible(userId, project.getId(), false))
                    {
                        ActionsHelper.addErrorToRequest(request, "resources_name[" + i + "]",
                                        new ActionMessage("error.com.cronos.onlinereview.actions.editProject.Resource.NotEligible"));

                        allResourcesValid=false;
                    }
                }
            }
        }

        return allResourcesValid;
    }

    /**
     * This method is an implementation of &quot;List Projects&quot; Struts Action defined for this
     * assembly, which is supposed to fetch list of projects from the database and pass it to the
     * JSP page for subsequent presentation to the end user.
     *
     * @return &quot;success&quot; forward, which forwards to the /jsp/listProjects.jsp page (as
     *         defined in struts-config.xml file).
     * @param mapping
     *            action mapping.
     * @param form
     *            action form.
     * @param request
     *            the HTTP request.
     * @param response
     *            the HTTP response.
     * @throws BaseException
     *             if any error occurs.
     */
    public ActionForward listProjects(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) throws BaseException {
        // Remove redirect-after-login attribute (if it exists)
        AuthorizationHelper.removeLoginRedirect(request);

        LoggingHelper.logAction(request);

        // Gather the roles the user has for current request
        AuthorizationHelper.gatherUserRoles(request);

        // Retrieve the value of "scope" parameter
        String scope = request.getParameter("scope");
        // Verify that "scope" parameter is specified and is not empty
        if (scope == null || scope.trim().length() == 0) {
            // Set default value for "scope" parameter, if previous condition has not been met
            scope = "my";
        }

        // If the user is trying to access pages he doesn't have permission to view,
        // redirect him to scope-all page, where public projects are listed
        if (scope.equalsIgnoreCase("my") && !AuthorizationHelper.isUserLoggedIn(request)) {
            return mapping.findForward("all");
        }
        if (scope.equalsIgnoreCase("inactive") &&
                !AuthorizationHelper.hasUserPermission(request, Constants.VIEW_PROJECTS_INACTIVE_PERM_NAME)) {
            return mapping.findForward("all");
        }

        // Obtain an instance of Project Manager
        ProjectManager manager = ActionsHelper.createProjectManager(request);
        // This variable will specify the index of active tab on the JSP page
        int activeTab;
        Filter projectsFilter = null;

        // Determine projects displayed and index of the active tab
        // based on the value of the "scope" parameter
        if (scope.equalsIgnoreCase("my")) {
            activeTab = 1;
        } else if (scope.equalsIgnoreCase("inactive")) {
            projectsFilter = ProjectFilterUtility.buildStatusNameEqualFilter("Inactive");
            activeTab = 4;
        } else {
            projectsFilter = ProjectFilterUtility.buildStatusNameEqualFilter("Active");

            // Specify the index of the active tab
            activeTab = 2;
        }

        // Pass the index of the active tab into request
        request.setAttribute("projectTabIndex", new Integer(activeTab));

        // Get all project types defined in the database (e.g. Assembly, Component, etc.)
        ProjectType[] projectTypes = manager.getAllProjectTypes();
        // Sort project types by their names in ascending order
        Arrays.sort(projectTypes, new Comparators.ProjectTypeComparer());
        // Get all project categories defined in the database (e.g. Design, Security, etc.)
        ProjectCategory[] projectCategories = manager.getAllProjectCategories();

        request.setAttribute("projectTypes", projectTypes);
        request.setAttribute("projectCategories", projectCategories);

        int[] typeCounts = new int[projectTypes.length];
        int[] categoryCounts = new int[projectCategories.length];
        String[] categoryIconNames = new String[projectCategories.length];

        // This is to signify whether "My" Projects list is displayed, or any other
        // type of Projects List. Some columns are present only in "My" Projects List
        boolean myProjects = scope.equalsIgnoreCase("my");

        Project[][] projects = new Project[projectCategories.length][];
        String[][] rootCatalogIcons = new String[projectCategories.length][];
        String[][] rootCatalogNames = new String[projectCategories.length][];
        Phase[][][] phases = new Phase[projectCategories.length][][];
        Date[][] phaseEndDates = new Date[projectCategories.length][];
        Date[][] projectEndDates = new Date[projectCategories.length][];

        // The following will only be non-null for the list of "My" Projects
        Resource[][][] myResources = (myProjects) ? new Resource[projectCategories.length][][] : null;
        String[][] myRoles = (myProjects) ? new String[projectCategories.length][] : null;
        String[][] myDeliverables = (myProjects) ? new String[projectCategories.length][] : null;

        // Fetch projects from the database. These projects will require further grouping
        Project[] ungroupedProjects = (projectsFilter != null) ? manager.searchProjects(projectsFilter) :
                manager.getUserProjects(AuthorizationHelper.getLoggedInUserId(request));

        // Sort fetched projects. Currently sorting is done by projects' names only, in ascending order
        Arrays.sort(ungroupedProjects, new Comparators.ProjectNameComparer());

        List<Long> projectFilters = new ArrayList<Long>();
        for (int i = 0; i < ungroupedProjects.length; ++i) {
            projectFilters.add(ungroupedProjects[i].getId());
        }

        Resource[] allMyResources = null;
        if (ungroupedProjects.length != 0 && AuthorizationHelper.isUserLoggedIn(request)) {

            Filter filterExtIDname = ResourceFilterBuilder.createExtensionPropertyNameFilter("External Reference ID");
            Filter filterExtIDvalue = ResourceFilterBuilder.createExtensionPropertyValueFilter(
                    String.valueOf(AuthorizationHelper.getLoggedInUserId(request)));


            Filter filterProjects = new InFilter(ResourceFilterBuilder.PROJECT_ID_FIELD_NAME, projectFilters);

            Filter filter = new AndFilter(Arrays.asList(
                    new Filter[] {filterExtIDname, filterExtIDvalue, filterProjects}));

            // Obtain an instance of Resource Manager
            ResourceManager resMgr = ActionsHelper.createResourceManager(request);
            // Get all "My" resources for the list of projects
            allMyResources = resMgr.searchResources(filter);
        }

        // new eligibility constraints
        // if the user is not a global manager and is seeing all projects eligibility checks need to be performed
        if (!AuthorizationHelper.hasUserRole(request, Constants.GLOBAL_MANAGER_ROLE_NAME) &&
            scope.equalsIgnoreCase("all") && projectFilters.size() > 0) {

            // remove those projects that the user can't see
            ungroupedProjects = filterUsingEligibilityConstraints(
                    ungroupedProjects, projectFilters, allMyResources);
        }

        // Obtain an instance of Phase Manager
        PhaseManager phMgr = ActionsHelper.createPhaseManager(request, false);

        long[] allProjectIds = new long[ungroupedProjects.length];

        for (int i = 0; i < ungroupedProjects.length; ++i) {
            allProjectIds[i] = ungroupedProjects[i].getId();
        }
        com.topcoder.project.phases.Project[] phProjects = phMgr.getPhases(allProjectIds);

        // Message Resources to be used for this request
        MessageResources messages = getResources(request);

        for (int i = 0; i < projectCategories.length; ++i) {
            // Count number of projects in this category
            for (int j = 0; j < ungroupedProjects.length; ++j) {
                if (ungroupedProjects[j].getProjectCategory().getId() == projectCategories[i].getId()) {
                    ++categoryCounts[i];
                }
            }

            /*
             * Now, as the exact count of projects in this category is known,
             * it is possible to initialize arrays
             */
            Project[] projs = new Project[categoryCounts[i]]; // This Category's Projects
            String[] rcIcons = new String[categoryCounts[i]]; // Root Catalog Icons
            String[] rcNames = new String[categoryCounts[i]]; // Root Catalog Names (shown in tooltip)
            Phase[][] phass = new Phase[categoryCounts[i]][]; // Projects' active Phases
            Date[] pheds = new Date[categoryCounts[i]]; // End date of every first active phase
            Date[] preds = new Date[categoryCounts[i]]; // Projects' end dates

            // No need to collect any Resources or Roles if
            // the list of projects is not just "My" Projects
            Resource[][] myRss = (myProjects) ? new Resource[categoryCounts[i]][] : null;
            String[] rols = (myProjects) ? new String[categoryCounts[i]] : null;

            if (categoryCounts[i] != 0) {
                // Counter of projects currently added to this category
                int counter = 0;
                // Copy ungrouped projects into group of this category
                for (int j = 0; j < ungroupedProjects.length; ++j) {
                    // Skip projects that are not in this category
                    // (they'll be processed later, or have already been processed)
                    if (ungroupedProjects[j].getProjectCategory().getId() != projectCategories[i].getId()) {
                        continue;
                    }

                    // Get a project to store in current group
                    Project project = ungroupedProjects[j];
                    // Get this project's Root Catalog ID
                    String rootCatalogId = (String)project.getProperty("Root Catalog ID");

                    // Fetch Root Catalog icon's filename depending on ID of the Root Catalog
                    rcIcons[counter] = ConfigHelper.getRootCatalogIconNameSm(rootCatalogId);
                    // Fetch Root Catalog name depending depending on ID of the Root Catalog
                    rcNames[counter] = messages.getMessage(ConfigHelper.getRootCatalogAltTextKey(rootCatalogId));

                    Phase[] activePhases = null;

                    // Calculate end date of the project and get all active phases (if any)
                    if (phProjects[j] != null) {
                        preds[counter] = phProjects[j].calcEndDate();
                        activePhases = ActionsHelper.getActivePhases(phProjects[j].getAllPhases());
                        pheds[counter] = null;
                    }

                    // Get currently open phase end calculate its end date
                    if (activePhases != null && activePhases.length != 0) {
                        phass[counter] = activePhases;
                        pheds[counter] = activePhases[0].getScheduledEndDate();
                    }

                    // Retrieve information about my roles, and my current unfinished deliverables
                    if (myProjects) {
                        Resource[] myResources2 = ActionsHelper.getResourcesForProject(allMyResources, project);
                        myRss[counter] = myResources2;
                        rols[counter] = getRolesFromResources(messages, myResources2);
                    }

                    // Store project in a group and increment counter
                    projs[counter] = project;
                    ++counter;
                }
            }

            // Save collected data in main arrays
            projects[i] = projs;
            rootCatalogIcons[i] = rcIcons;
            rootCatalogNames[i] = rcNames;
            phases[i] = phass;
            phaseEndDates[i] = pheds;
            projectEndDates[i] = preds;

            // Resources and roles must not always be saved
            if (myProjects) {
                myResources[i] = myRss;
                myRoles[i] = rols;
            }

            // Fetch Project Category icon's filename depending on the name of the current category
            categoryIconNames[i] = ConfigHelper.getProjectCategoryIconNameSm(projectCategories[i].getName());
        }

        if (ungroupedProjects.length != 0 && myProjects) {
            Deliverable[] allMyDeliverables = getDeliverables(
                    ActionsHelper.createDeliverableManager(request), projects, phases, myResources);

            // Group the deliverables per projects in list
            for (int i = 0; i < projects.length; ++i) {
                String[] deliverables = new String[projects[i].length];
                for (int j = 0; j < projects[i].length; ++j) {
                    String winnerIdStr = (String) projects[i][j].getProperty("Winner External Reference ID");
                    if (winnerIdStr != null && winnerIdStr.trim().length() == 0) {
                        winnerIdStr = null;
                    }

                    deliverables[j] = getMyDeliverablesForPhases(
                            messages, allMyDeliverables, phases[i][j], myResources[i][j], winnerIdStr);
                }
                myDeliverables[i] = deliverables;
            }
        }

        int totalProjectsCount = 0;

        // Count projects in every type group now
        for (int i = 0; i < projectTypes.length; ++i) {
            for (int j = 0; j < projectCategories.length; ++j) {
                if (projectCategories[j].getProjectType().getId() == projectTypes[i].getId()) {
                    typeCounts[i] += categoryCounts[j];
                }
            }
            totalProjectsCount += typeCounts[i];
        }

        // Place all collected data into the request as attributes
        request.setAttribute("projects", projects);
        request.setAttribute("rootCatalogIcons", rootCatalogIcons);
        request.setAttribute("rootCatalogNames", rootCatalogNames);
        request.setAttribute("phases", phases);
        request.setAttribute("phaseEndDates", phaseEndDates);
        request.setAttribute("projectEndDates", projectEndDates);
        request.setAttribute("typeCounts", typeCounts);
        request.setAttribute("categoryCounts", categoryCounts);
        request.setAttribute("totalProjectsCount", new Integer(totalProjectsCount));
        request.setAttribute("categoryIconNames", categoryIconNames);

        // If the currently displayed list is a list of "My" Projects, add some more attributes
        if (myProjects) {
            request.setAttribute("isMyProjects", new Boolean(myProjects));
            request.setAttribute("myRoles", myRoles);
            request.setAttribute("myDeliverables", myDeliverables);
        }

        // Signal about successful execution of the Action
        return mapping.findForward(Constants.SUCCESS_FORWARD_NAME);
    }

    /**
     * This method will return an array of <code>Project</code> with those projects the user can see taking into
     * consideration eligibility constraints.
     *
     * The user can see all those "public" (no eligibility constraints) projects plus those non-public projects where
     * he is assigned as a resource.
     *
     * @param ungroupedProjects all project to be displayed
     * @param projectFilters all project ids to be displayed
     * @param allMyResources all resources the user has for the projects to be displayed
     *
     * @return a <code>Project[]</code> with those projects that the user can see.
     *
     * @throws BaseException if any error occurs during eligibility services call
     *
     * @since 1.4
     */
    private Project[] filterUsingEligibilityConstraints(Project[] ungroupedProjects, List<Long> projectFilters,
            Resource[] allMyResources) throws BaseException {
        // check which projects have eligibility constraints
        Set<Long> projectsWithEligibilityConstraints;
        try {
            projectsWithEligibilityConstraints =
                ContestEligibilityServiceLocator.getServices().haveEligibility(
                    projectFilters.toArray(new Long[projectFilters.size()]), false);
        } catch (Exception e) {
            log.error("It was not possible to retrieve eligibility constraints: "+e);
            throw new BaseException("It was not possible to retrieve eligibility constraints", e);
        }

        // create a set of projects where the user is a resource
        Set<Long> resourceProjects = new HashSet<Long>();
        if (allMyResources != null) {
            for (Resource r: allMyResources) {
                resourceProjects.add(r.getProject());
            }
        }

        // user can see those projects with eligibility constraints where he is a resource, so remove these
        // from the projectsWithEligibilityConstraints set
        projectsWithEligibilityConstraints.removeAll(resourceProjects);

        // finally remove those projects left in projectsWithEligibilityConstraints from ungroupedProjects
        List<Project> visibleProjects = new ArrayList<Project>();
        for (Project p : ungroupedProjects) {
            if (!projectsWithEligibilityConstraints.contains(p.getId())) {
                visibleProjects.add(p);
            }
        }

        ungroupedProjects = visibleProjects.toArray(new Project[visibleProjects.size()]);
        return ungroupedProjects;
    }

    /**
     * This static method performs a search for all outstanding deliverables. The list of these
     * deliverables is returned as is, i.e. as one-dimensional array, and will require further
     * grouping.
     *
     * @return an array of outstanding (incomplete) deliverables.
     * @param manager
     *            an instance of <code>DeliverableManager</code> class that will be used to
     *            perform a search for deliverables.
     * @param projects
     *            an array of the projects to search the deliverables for.
     * @param phases
     *            an array of active phases for the projects specified by <code>projects</code>
     *            parameter. The deliverables found will only be related to these phases.
     * @param resources
     *            an array of resources to search the deliverables for. Each of the deliverables
     *            found will have to be complited by one of the resources from this array.
     * @throws IllegalArgumentException
     *             if any of the parameters are <code>null</code>.
     * @throws DeliverablePersistenceException
     *             if there is an error reading the persistence store.
     * @throws SearchBuilderException
     *             if there is an error executing the filter.
     * @throws DeliverableCheckingException
     *             if there is an error determining whether some Deliverable has been completed or
     *             not.
     */
    private static Deliverable[] getDeliverables(DeliverableManager manager, Project[][] projects, Phase[][][] phases,
            Resource[][][] resources)
            throws DeliverablePersistenceException, SearchBuilderException, DeliverableCheckingException {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(manager, "manager");
        ActionsHelper.validateParameterNotNull(projects, "projects");
        ActionsHelper.validateParameterNotNull(phases, "phases");
        ActionsHelper.validateParameterNotNull(resources, "resources");

        List<Long> projectIds = new ArrayList<Long>();
        List<Long> phaseTypeIds = new ArrayList<Long>();
        List<Long> resourceIds = new ArrayList<Long>();

        for (int i = 0; i < projects.length; ++i) {
            for (int j = 0; j < projects[i].length; ++j) {
                projectIds.add(projects[i][j].getId());

                // Get an array of active phases for the project
                Phase[] activePhases = phases[i][j];
                // If there are no active phases, no need to select deliverables for this project
                if (activePhases == null) {
                    continue;
                }

                for (int k = 0; k < activePhases.length; ++k) {
                    phaseTypeIds.add(activePhases[k].getId());
                }

                // Get an array of "my" resources for the active phases
                Resource[] myResources = resources[i][j];
                // If there are no "my" resources, skip the rest of the loop
                if (myResources == null) {
                    continue;
                }

                for (int k = 0; k < myResources.length; ++k) {
                    resourceIds.add(myResources[k].getId());
                }
            }
        }

        // If any of the sets is empty, there cannot be any deliverables
        if (projectIds.isEmpty() || phaseTypeIds.isEmpty() || resourceIds.isEmpty()) {
            return new Deliverable[0]; // No deliverables
        }

        // Build filters to select deliverables
        Filter filterProjects = new InFilter("project_id", projectIds);
        Filter filterPhases = new InFilter("phase_id", phaseTypeIds);
        Filter filterResources = new InFilter("resource_id", resourceIds);
        // Build final combined filter
        Filter filter = new AndFilter(Arrays.asList(new Filter[] {filterProjects, filterPhases, filterResources}));

        // Get and return an array of my incomplete deliverables for all active phases.
        // These deliverables will require further grouping
        return manager.searchDeliverables(filter, Boolean.FALSE);
    }

    /**
     * This static method returns a string that lists all the different roles the resources
     * specified by <code>resources</code> array have. The roles will be delimited by
     * line-breaking tag (<code>&lt;br&#160;/&gt;</code>). If there are no resources in
     * <code>resources</code> array or no roles have been found, this method returns a string that
     * denotes Public role (usually this string just says &quot;Public&quot;).
     *
     * @return a human-readable list of resource roles.
     * @param messages
     *            an instance of <code>MessageResources</code> class used to retrieve textual
     *            representation of resource roles in different locales.
     * @param resources
     *            an array of the roles to determine the names of their resource roles.
     * @throws IllegalArgumentException
     *             if any of the parameters are <code>null</code>.
     */
    private static String getRolesFromResources(MessageResources messages, Resource[] resources) {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(messages, "messages");
        ActionsHelper.validateParameterNotNull(resources, "resources");

        if (resources == null || resources.length == 0) {
            return messages.getMessage("ResourceRole.Public");
        }

        StringBuffer buffer = new StringBuffer();
        Set<String> rolesSet = new HashSet<String>();

        for (int i = 0; i < resources.length; ++i) {
            // Get the name for a resource in the current iteration
            String resourceRole = resources[i].getResourceRole().getName();

            if (rolesSet.contains(resourceRole)) {
                continue;
            }

            if (buffer.length() != 0) {
                buffer.append("<br />");
            }
            buffer.append(messages.getMessage("ResourceRole." + resourceRole.replaceAll(" ", "")));
            rolesSet.add(resourceRole);
        }

        return (buffer.length() != 0) ? buffer.toString() : messages.getMessage("ResourceRole.Public");
    }

    /**
     * This static method returns a string that lists all the different outstanding (i.e.
     * incomplete) deliverables the resources specified by <code>resources</code> array have. The
     * deliverables will be delimited by line-breaking tag (<code>&lt;br&#160;/&gt;</code>). If
     * any of the arrays passed to this method is <code>null</code> or empty, or no deliverables
     * have been found, this method returns empty string.
     *
     * @return a human-readable list of deliverables.
     * @param messages
     *            an instance of <code>MessageResources</code> class used to retrieve textual
     *            representation of deliverables' names in different locales.
     * @param deliverables
     *            an array of deliverables to fetch outstanding deliverables (and their names) from.
     * @param phases
     *            an array of phases to look up the deliverables for.
     * @param resources
     *            an array of resources to look up the deliverables for.
     * @param winnerExtUserId
     *            an External User ID of the winning user for the project, if any. If there is no
     *            winner for the project, this parameter must be <code>null</code>.
     * @throws IllegalArgumentException
     *             if parameter <code>messages</code> is <code>null</code>.
     */
    private static String getMyDeliverablesForPhases(MessageResources messages,
            Deliverable[] deliverables, Phase[] phases, Resource[] resources, String winnerExtUserId) {
        // Validate parameters
        ActionsHelper.validateParameterNotNull(messages, "messages");

        if (deliverables == null || deliverables.length == 0 ||
                phases == null || phases.length == 0 ||
                resources == null || resources.length == 0) {
            return null; // No deliverables
        }

        StringBuffer buffer = new StringBuffer();
        Set<String> deliverablesSet = new HashSet<String>();

        for (int i = 0; i < deliverables.length; ++i) {
            // Get a deliverable for the current iteration
            Deliverable deliverable = deliverables[i];

            // Check if this deliverable is for any of the phases in question
            int j = 0;

            for (;j < phases.length; ++j) {
                if (deliverable.getPhase() == phases[j].getId()) {
                    break;
                }
            }
            // If this deliverable is not for any of the phases, continue the search
            if (j == phases.length) {
                continue;
            }

            for (j = 0;j < resources.length; ++j) {
                if (deliverable.getResource() == resources[j].getId()) {
                    break;
                }
            }
            // If this deliverable is not for any of the resources, continue the search
            if (j == resources.length) {
                continue;
            }

            // Get a resource this deliverable is for
            final Resource forResource = resources[j];

            // Some additional special checking is need for Aggregation Review type of deliverables
            if (deliverable.getName().equalsIgnoreCase(Constants.AGGREGATION_REV_DELIVERABLE_NAME)) {
                // Get the name of the resource's role
                final String resourceRole = forResource.getResourceRole().getName();
                // Check that this deliverable is for one of the reviewers
                if (resourceRole.equalsIgnoreCase(Constants.REVIEWER_ROLE_NAME) ||
                        resourceRole.equalsIgnoreCase(Constants.ACCURACY_REVIEWER_ROLE_NAME) ||
                        resourceRole.equalsIgnoreCase(Constants.FAILURE_REVIEWER_ROLE_NAME) ||
                        resourceRole.equalsIgnoreCase(Constants.STRESS_REVIEWER_ROLE_NAME)) {
                    final String originalExtId = (String) forResource.getProperty("External Reference ID");

                    // Iterate over all resources and check
                    // if there is any resource assigned to the same user
                    for (j = 0; j < resources.length; ++j) {
                        // Skip resource that is being checked
                        if (forResource == resources[j]) {
                            continue;
                        }

                        // Get a resource for the current iteration
                        final Resource otherResource = resources[j];
                        // Verify whether this resource is an Aggregator, and skip it if it isn't
                        if (!otherResource.getResourceRole().getName().equalsIgnoreCase(Constants.AGGREGATOR_ROLE_NAME)) {
                            continue;
                        }

                        String otherExtId = (String) resources[j].getProperty("External Reference ID");
                        // If appropriate aggregator's resource has been found, stop the search
                        if (originalExtId.equals(otherExtId)) {
                            break;
                        }
                    }
                    // Skip this deliverable if it is assigned to aggregator
                    if (j != resources.length) {
                        continue;
                    }
                }
            }

            // Skip deliverables that are not for winning submitter
            if (winnerExtUserId != null) {
                if (forResource.getResourceRole().getName().equalsIgnoreCase(Constants.SUBMITTER_ROLE_NAME) &&
                        !winnerExtUserId.equals(resources[j].getProperty("External Reference ID"))) {
                    continue;
                }
            }

            // Get the name of the deliverable
            String deliverableName = deliverable.getName();
            // Do not add the same deliverable twice
            if (deliverablesSet.contains(deliverableName)) {
                continue;
            }

            // If this is not the first deliverable, add line-breaking tag
            if (buffer.length() != 0) {
                buffer.append("<br />");
            }
            buffer.append(messages.getMessage("Deliverable." + deliverableName.replaceAll(" ", "")));
            deliverablesSet.add(deliverableName);
        }

        return (buffer.length() != 0) ? buffer.toString() : null;
    }

    /**
     * <p>Checks if specified resource is assigned <code>Submitter</code> role or not.</p>
     *
     * @param resource a <code>Resource</code> to be verified.
     * @return <code>true</code> if specified resource is assigned <code>Submitter</code> role; <code>false</code>
     *         otherwise.
     * @since BUGR-2807
     */
    private static boolean isSubmitter(Resource resource) {
        ResourceRole role = resource.getResourceRole();
        return (role != null) && (role.getId() == 1);
    }

    /**
     * <p>Builds the map to be used for looking up the project categories by IDs.</p>
     *
     * @param categories a <code>ProjectCategory</code> array listing existing project categories. 
     * @return a <code>Map</code> mapping the category IDs to categories.
     * @since 1.6
     */
    private static Map<Long, ProjectCategory> buildProjectCategoriesLookupMap(ProjectCategory[] categories) {
        Map<Long, ProjectCategory> map = new HashMap<Long, ProjectCategory>();
        for (ProjectCategory category : categories) {
            map.put(category.getId(), category);
        }
        return map;
    }
}
