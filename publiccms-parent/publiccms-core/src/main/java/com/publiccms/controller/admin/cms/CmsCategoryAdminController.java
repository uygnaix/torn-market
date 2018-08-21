package com.publiccms.controller.admin.cms;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import com.publiccms.common.base.AbstractController;
import com.publiccms.common.constants.CommonConstants;
import com.publiccms.common.tools.CommonUtils;
import com.publiccms.common.tools.ControllerUtils;
import com.publiccms.common.tools.JsonUtils;
import com.publiccms.common.tools.RequestUtils;
import com.publiccms.entities.cms.CmsCategory;
import com.publiccms.entities.cms.CmsCategoryAttribute;
import com.publiccms.entities.log.LogOperate;
import com.publiccms.entities.sys.SysSite;
import com.publiccms.logic.component.template.TemplateComponent;
import com.publiccms.logic.service.cms.CmsCategoryService;
import com.publiccms.logic.service.cms.CmsContentService;
import com.publiccms.logic.service.log.LogLoginService;
import com.publiccms.views.pojo.model.CmsCategoryParameters;

import freemarker.template.TemplateException;

/**
 * 
 * CmsCategoryController
 *
 */
@Controller
@RequestMapping("cmsCategory")
public class CmsCategoryAdminController extends AbstractController {
    @Autowired
    private CmsCategoryService service;
    @Autowired
    private CmsContentService contentService;
    @Autowired
    private TemplateComponent templateComponent;

    private String[] ignoreProperties = new String[] { "siteId", "childIds", "tagTypeIds", "url", "disabled", "extendId",
            "contents", "typeId" };

    /**
     * @param entity
     * @param attribute
     * @param categoryParameters
     * @param _csrf
     * @param request
     * @param session
     * @param model
     * @return view name
     */
    @RequestMapping("save")
    public String save(CmsCategory entity, CmsCategoryAttribute attribute,
            @ModelAttribute CmsCategoryParameters categoryParameters, String _csrf, HttpServletRequest request,
            HttpSession session, ModelMap model) {
        if (ControllerUtils.verifyNotEquals("_csrf", ControllerUtils.getAdminToken(request), _csrf, model)) {
            return CommonConstants.TEMPLATE_ERROR;
        }
        SysSite site = getSite(request);
        if (null != entity.getId()) {
            CmsCategory oldEntity = service.getEntity(entity.getId());
            if (null == oldEntity || ControllerUtils.verifyNotEquals("siteId", site.getId(), oldEntity.getSiteId(), model)) {
                return CommonConstants.TEMPLATE_ERROR;
            }
            entity = service.update(entity.getId(), entity, ignoreProperties);
            if (null != entity) {
                service.generateChildIds(site.getId(), oldEntity.getParentId(), entity.getParentId());
                logOperateService.save(new LogOperate(site.getId(), ControllerUtils.getAdminFromSession(session).getId(),
                        LogLoginService.CHANNEL_WEB_MANAGER, "update.category", RequestUtils.getIpAddress(request),
                        CommonUtils.getDate(), JsonUtils.getString(entity)));
            }
        } else {
            service.save(site.getId(), entity);
            logOperateService.save(new LogOperate(site.getId(), ControllerUtils.getAdminFromSession(session).getId(),
                    LogLoginService.CHANNEL_WEB_MANAGER, "save.category", RequestUtils.getIpAddress(request),
                    CommonUtils.getDate(), JsonUtils.getString(entity)));
        }
        service.saveTagAndAttribute(site.getId(), entity.getId(), attribute, categoryParameters);
        try {
            publish(site, entity.getId(), null);
        } catch (IOException | TemplateException e) {
            ControllerUtils.verifyCustom("static", true, model);
            log.error(e.getMessage(), e);
        }
        return CommonConstants.TEMPLATE_DONE;
    }

    /**
     * @param ids
     * @param parentId
     * @param _csrf
     * @param request
     * @param session
     * @param model
     * @return view name
     */
    @RequestMapping("move")
    public String move(Integer[] ids, Integer parentId, String _csrf, HttpServletRequest request, HttpSession session,
            ModelMap model) {
        if (ControllerUtils.verifyNotEquals("_csrf", ControllerUtils.getAdminToken(request), _csrf, model)) {
            return CommonConstants.TEMPLATE_ERROR;
        }
        SysSite site = getSite(request);
        CmsCategory parent = service.getEntity(parentId);
        if (CommonUtils.notEmpty(ids) && (null == parent || null != parent && site.getId() == parent.getSiteId())) {
            for (Integer id : ids) {
                move(site, id, parentId);
            }
            logOperateService.save(new LogOperate(site.getId(), ControllerUtils.getAdminFromSession(session).getId(),
                    LogLoginService.CHANNEL_WEB_MANAGER, "move.category", RequestUtils.getIpAddress(request),
                    CommonUtils.getDate(),
                    new StringBuilder(StringUtils.join(ids, ',')).append(" to ").append(parentId).toString()));
        }
        return CommonConstants.TEMPLATE_DONE;
    }

    /**
     * @param siteId
     * @param id
     * @param parentId
     */
    private void move(SysSite site, Integer id, Integer parentId) {
        CmsCategory entity = service.getEntity(id);
        if (null != entity && site.getId() == entity.getSiteId()) {
            service.updateParentId(site.getId(), id, parentId);
            service.generateChildIds(site.getId(), entity.getParentId());
            if (null != parentId) {
                service.generateChildIds(site.getId(), parentId);
            }
        }
    }

    /**
     * @param ids
     * @param max
     * @param _csrf
     * @param request
     * @param session
     * @param model
     * @return view name
     */
    @RequestMapping("publish")
    public String publish(Integer[] ids, Integer max, String _csrf, HttpServletRequest request, HttpSession session,
            ModelMap model) {
        if (ControllerUtils.verifyNotEquals("_csrf", ControllerUtils.getAdminToken(request), _csrf, model)) {
            return CommonConstants.TEMPLATE_ERROR;
        }
        SysSite site = getSite(request);
        if (CommonUtils.notEmpty(ids)) {
            try {
                for (Integer id : ids) {
                    publish(site, id, max);
                }
            } catch (IOException | TemplateException e) {
                ControllerUtils.verifyCustom("static", true, model);
                log.error(e.getMessage(), e);
                return CommonConstants.TEMPLATE_ERROR;
            }
            logOperateService.save(new LogOperate(site.getId(), ControllerUtils.getAdminFromSession(session).getId(),
                    LogLoginService.CHANNEL_WEB_MANAGER, "static.category", RequestUtils.getIpAddress(request),
                    CommonUtils.getDate(), new StringBuilder(StringUtils.join(ids, ',')).append(",pageSize:")
                            .append((CommonUtils.empty(max) ? 1 : max)).toString()));
        }
        return CommonConstants.TEMPLATE_DONE;
    }

    /**
     * @param id
     * @param typeId
     * @param _csrf
     * @param request
     * @param session
     * @param model
     * @return view name
     */
    @RequestMapping("changeType")
    public String changeType(Integer id, Integer typeId, String _csrf, HttpServletRequest request, HttpSession session,
            ModelMap model) {
        if (ControllerUtils.verifyNotEquals("_csrf", ControllerUtils.getAdminToken(request), _csrf, model)) {
            return CommonConstants.TEMPLATE_ERROR;
        }
        SysSite site = getSite(request);
        if (CommonUtils.notEmpty(id)) {
            service.changeType(id, typeId);
            logOperateService.save(new LogOperate(site.getId(), ControllerUtils.getAdminFromSession(session).getId(),
                    LogLoginService.CHANNEL_WEB_MANAGER, "changeType.category", RequestUtils.getIpAddress(request),
                    CommonUtils.getDate(), new StringBuilder(id).append(" to ").append(typeId).toString()));
        }
        return CommonConstants.TEMPLATE_DONE;
    }

    /**
     * @param siteId
     * @param id
     * @param max
     * @throws IOException
     * @throws TemplateException
     */
    private void publish(SysSite site, Integer id, Integer max) throws IOException, TemplateException {
        CmsCategory entity = service.getEntity(id);
        if (null != site && null != entity && site.getId() == entity.getSiteId()) {
            templateComponent.createCategoryFile(site, entity, null, max);
        }
    }

    /**
     * @param ids
     * @param _csrf
     * @param request
     * @param session
     * @param model
     * @return view name
     */
    @RequestMapping("delete")
    public String delete(Integer[] ids, String _csrf, HttpServletRequest request, HttpSession session, ModelMap model) {
        if (ControllerUtils.verifyNotEquals("_csrf", ControllerUtils.getAdminToken(request), _csrf, model)) {
            return CommonConstants.TEMPLATE_ERROR;
        }
        if (CommonUtils.notEmpty(ids)) {
            SysSite site = getSite(request);
            service.delete(site.getId(), ids);
            contentService.deleteByCategoryIds(site.getId(), ids);
            logOperateService.save(new LogOperate(site.getId(), ControllerUtils.getAdminFromSession(session).getId(),
                    LogLoginService.CHANNEL_WEB_MANAGER, "delete.category", RequestUtils.getIpAddress(request),
                    CommonUtils.getDate(), StringUtils.join(ids, ',')));
        }
        return CommonConstants.TEMPLATE_DONE;
    }
}