package dk.magenta.webscripts;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.jscript.BaseScopableProcessorExtension;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.thumbnail.script.ScriptThumbnail;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.remoteconnector.RemoteConnectorRequest;
import org.alfresco.service.cmr.remoteconnector.RemoteConnectorResponse;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.thumbnail.ThumbnailService;
import org.apache.commons.lang.StringUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.springframework.extensions.webscripts.*;
import org.alfresco.repo.remoteconnector.RemoteConnectorServiceImpl;
import ucar.nc2.iosp.bufr.Message;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * Based on Alfresco's thumbnail.get.js
 *
 * The main difference is that it modifies the HTML output such that it
 * includes an arbitrary string in the <head></head> tag of the output.
 *
 * Created by syastrov on 9/14/15.
 */
public class HTMLIFrameThumbnailGetWebscript extends AbstractWebScript {
    private String stringToInsertInHeadTag;
    private NodeService nodeService;
    private DictionaryService dictionaryService;
    private ServiceRegistry serviceRegistry;
    private ContentService contentService;

    private ThumbnailService thumbnailService;

    public void setThumbnailService(ThumbnailService thumbnailService) {
        this.thumbnailService = thumbnailService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    public void setStringToInsertInHeadTag(String stringToInsertInHeadTag) {
        this.stringToInsertInHeadTag = stringToInsertInHeadTag;
    }

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        Map<String, String> templateArgs = req.getServiceMatch().getTemplateVars();
        NodeRef nodeRef = new NodeRef(templateArgs.get("store_type"), templateArgs.get("store_id"), templateArgs.get("id"));
        if (!nodeService.exists(nodeRef)) {
            res.setStatus(Status.STATUS_NOT_FOUND);
            return;
        }

        if (!dictionaryService.isSubClass(nodeService.getType(nodeRef),
                ContentModel.TYPE_CONTENT)) {
            res.setStatus(Status.STATUS_BAD_REQUEST);
            return;
        }

        String thumbnailName = templateArgs.get("thumbnailname");
        if (StringUtils.isEmpty(thumbnailName)) {
            res.setStatus(Status.STATUS_NOT_FOUND);
            return;
        }

        if (!thumbnailName.equals("html")) {
            throw new WebScriptException(HttpServletResponse.SC_BAD_REQUEST,
                    "This script only supports returning 'html' thumbnails");
        }

        NodeRef thumbnailNodeRef = thumbnailService.getThumbnailByName(nodeRef, ContentModel.PROP_CONTENT, thumbnailName);
        Context cx = Context.enter();
        Scriptable scope = cx.initStandardObjects();
        ScriptNode scriptNode = new ScriptNode(nodeRef, serviceRegistry, scope);
        ScriptThumbnail thumbnail = scriptNode.getThumbnail(thumbnailName);
        if (thumbnail == null || thumbnail.getSize() == 0) {
            // Remove broken thumbnail
            if (thumbnail != null) {
                thumbnail.remove();
            }

            // Get the queue/force create setting
            String c = req.getParameter("c");
            boolean qc = false;
            boolean fc = false;
            if (StringUtils.isNotEmpty(c)) {
                if (c.equals("queue")) {
                    qc = true;
                } else if (c.equals("force")) {
                    fc = true;
                }
            }

            if (fc) {
                thumbnail = scriptNode.createThumbnail(thumbnailName, false, true);
                if (thumbnail != null) {
                    thumbnailNodeRef = thumbnail.getNodeRef();
                }
            } else {
                if (qc) {
                    scriptNode.createThumbnail(thumbnailName, true);
                }
            }

            if (thumbnailNodeRef == null) {
                throw new WebScriptException(HttpServletResponse.SC_NOT_FOUND, "Thumbnail was not found");
            }
        }


        ContentReader reader = contentService.getReader(thumbnailNodeRef, ContentModel.PROP_CONTENT);
        if (!reader.getMimetype().equals(MimetypeMap.MIMETYPE_HTML)) {
            throw new WebScriptException(HttpServletResponse.SC_BAD_REQUEST,
                    "This script only supports returning thumbnails of type HTML");
        }

        String contentString = reader.getContentString();
        // Read in the the HTML content and modify it
        contentString = modifyHTML(contentString);

        // Return it
        res.setContentType(MimetypeMap.MIMETYPE_HTML);
        Writer writer = res.getWriter();
        writer.write(contentString);
    }

    private String modifyHTML(String input) {
        StringBuilder builder = new StringBuilder(input);
        int headEnd = input.indexOf("</head>");
        // Insert an arbitrary String into the <head> tag of the String
        builder.insert(headEnd, stringToInsertInHeadTag);
        return builder.toString();
    }
}
