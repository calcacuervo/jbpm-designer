package org.jbpm.designer.web.server;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import bpsim.impl.BpsimFactoryImpl;
import org.apache.log4j.Logger;
import org.eclipse.bpmn2.Definitions;
import org.jboss.drools.impl.DroolsFactoryImpl;
import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.ioc.client.api.Caller;
import org.jbpm.designer.bpmn2.impl.Bpmn2JsonUnmarshaller;
import org.jbpm.designer.repository.Asset;
import org.jbpm.designer.repository.AssetBuilderFactory;
import org.jbpm.designer.repository.Repository;
import org.jbpm.designer.repository.impl.AssetBuilder;
import org.jbpm.designer.taskforms.TaskFormInfo;
import org.jbpm.designer.taskforms.TaskFormTemplateManager;
import org.jbpm.designer.util.ConfigurationProvider;
import org.jbpm.designer.web.profile.IDiagramProfile;
import org.jbpm.designer.web.profile.IDiagramProfileService;

import org.jbpm.formModeler.designer.integration.BPMNFormBuilderService;
import org.uberfire.backend.vfs.Path;
import org.uberfire.backend.vfs.VFSService;
import org.uberfire.util.URIEncoder;

/** 
 * 
 * Creates/updates task forms for a specific process.
 * 
 * @author Tihomir Surdilovic
 */
public class TaskFormsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger _logger = Logger
            .getLogger(TaskFormsServlet.class);
    private static final String TASKFORMS_PATH = "taskforms";
    private static final String FORMTEMPLATE_FILE_EXTENSION = "ftl";
    private static final String FORMMODELER_FILE_EXTENSION = "form";
    public static final String DESIGNER_PATH = ConfigurationProvider.getInstance().getDesignerContext();

    private IDiagramProfile profile;

    public void setProfile(IDiagramProfile profile) {
        this.profile = profile;
    }

    @Inject
    private IDiagramProfileService _profileService = null;

    @Inject
    private BPMNFormBuilderService formModelerService;

    @Inject
    private VFSService vfsServices;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String json = req.getParameter("json");
        String uuid = req.getParameter("uuid");
        String profileName = req.getParameter("profile");
        String preprocessingData = req.getParameter("ppdata");
        if (profile == null) {
            profile = _profileService.findProfile(req, profileName);
        }
        Repository repository = profile.getRepository();

        Asset<String> processAsset = null;

        try {
            processAsset = repository.loadAsset(uuid);

            DroolsFactoryImpl.init();
            BpsimFactoryImpl.init();

            Bpmn2JsonUnmarshaller unmarshaller = new Bpmn2JsonUnmarshaller();
            Definitions def = ((Definitions) unmarshaller.unmarshall(json, preprocessingData).getContents().get(0));

            Path myPath = vfsServices.get( uuid );

            TaskFormTemplateManager templateManager = new TaskFormTemplateManager( myPath, formModelerService, profile, processAsset, getServletContext().getRealPath(DESIGNER_PATH + TASKFORMS_PATH), def );
            templateManager.processTemplates();

            storeInRepository(templateManager, processAsset.getAssetLocation(), repository);
            //displayResponse( templateManager, resp, profile );
            resp.setContentType("text/plain");
            resp.getWriter().write("success");
        } catch (Exception e) {
            _logger.error(e.getMessage());
            //displayErrorResponse(resp, e.getMessage());
            resp.setContentType("text/plain");
            resp.getWriter().write("fail");
        }
    }
    
//    public void displayResponse(TaskFormTemplateManager templateManager, HttpServletResponse resp, IDiagramProfile profile) {
//        try {
//            StringTemplateGroup templates = new StringTemplateGroup("resultsgroup", templateManager.getTemplatesPath());
//            StringTemplate resultsForm = templates.getInstanceOf("resultsform");
////            resultsForm.setAttribute("manager", templateManager);
////            resultsForm.setAttribute("profile", RepositoryInfo.getRepositoryProtocol(profile));
////            resultsForm.setAttribute("host", RepositoryInfo.getRepositoryHost(profile));
////            resultsForm.setAttribute("subdomain", RepositoryInfo.getRepositorySubdomain(profile).substring(0,
////                RepositoryInfo.getRepositorySubdomain(profile).indexOf("/")));
//            ServletOutputStream outstr = resp.getOutputStream();
//            resp.setContentType("text/html");
//            outstr.write(resultsForm.toString().getBytes("UTF-8"));
//            outstr.flush();
//            outstr.close();
//        } catch (IOException e) {
//           _logger.error(e.getMessage());
//        }
//    }
    
//    public void displayErrorResponse(HttpServletResponse resp, String exceptionStr) {
//        try {
//            ServletOutputStream outstr = resp.getOutputStream();
//            resp.setContentType("text/html");
//            outstr.write(exceptionStr.getBytes("ASCII"));
//            outstr.flush();
//            outstr.close();
//        } catch (IOException e) {
//           _logger.error(e.getMessage());
//        }
//    }
    
    public void storeInRepository(TaskFormTemplateManager templateManager, String location, Repository repository) throws Exception {
        List<TaskFormInfo> taskForms =  templateManager.getTaskFormInformationList();
        for(TaskFormInfo taskForm : taskForms) {
            storeTaskForm(taskForm, location, repository);
        }
    }
    
    public void storeTaskForm(TaskFormInfo taskForm, String location, Repository repository) throws Exception {
        try {

            repository.deleteAssetFromPath("/" + taskForm.getPkgName() + "/" + taskForm.getId()+"."+FORMTEMPLATE_FILE_EXTENSION);

            // create the form meta form asset
            AssetBuilder builder = AssetBuilderFactory.getAssetBuilder(Asset.AssetType.Byte);
            builder.name(taskForm.getId())
                   .location(location)
                    .type(FORMTEMPLATE_FILE_EXTENSION)
                    .content(taskForm.getMetaOutput().getBytes("UTF-8"));

            repository.createAsset(builder.getAsset());

            // create the modeler form asset
            repository.deleteAssetFromPath("/" + taskForm.getPkgName() + "/" + taskForm.getId()+"."+FORMMODELER_FILE_EXTENSION);
            AssetBuilder modelerBuilder = AssetBuilderFactory.getAssetBuilder(Asset.AssetType.Byte);
            modelerBuilder.name(taskForm.getId())
                    .location(location)
                    .type(FORMMODELER_FILE_EXTENSION)
                    .content(taskForm.getModelerOutput().getBytes("UTF-8"));

            repository.createAsset(modelerBuilder.getAsset());

		} catch (Exception e) {
			_logger.error(e.getMessage());
		}
    }
}
