package com.hzy.blog.controller;

import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.hzy.blog.vo.ArticleTypeTreeVo;
import org.apdplat.word.WordSegmenter;
import org.apdplat.word.segmentation.Word;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hzy.blog.dto.user.UserInfoDto;
import com.hzy.blog.entity.*;
import com.hzy.blog.service.*;
import com.hzy.blog.utils.CommonPage;
import com.hzy.blog.utils.CommonResult;
import com.hzy.blog.utils.CommonUtils;
import com.hzy.blog.vo.ArticleVo;
import com.hzy.blog.vo.CommentVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Fa Xiaoji 14439
 * @date 2024/4/27 19:20
 */
@Controller
public class ViewController {

    @Resource
    private IUserService userService;
    @Resource
    private IArticleService articleService;
    @Resource
    private IArticleTypeService articleTypeService;
    @Resource
    private IArticleTagService articleTagService;
    @Resource
    private IArticleTagListService articleTagListService;
    @Resource
    private ICommentService commentService;
    @Resource
    private ICommentReplyService commentReplyService;
    @Resource
    private ServletContext servletContext;
    @Resource
    private IAdTypeService adTypeService;
    @Resource
    private IAdService adService;
    @Resource
    private ILinkService linkService;

    /**
     * 清除首页缓存
     *
     * @return
     */
    @GetMapping("/ci")
    public String clearCache() {
        servletContext.removeAttribute("articleTypeList");
        servletContext.removeAttribute("articleHotList");
        servletContext.removeAttribute("articleTagList");
        servletContext.removeAttribute("adIndexList");
        servletContext.removeAttribute("linkList");
        return "redirect:/";
    }

    /**
     * 获取图像验证码
     *
     * @throws IOException
     */
    @GetMapping("/getCaptcha")
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CircleCaptcha captcha = CommonUtils.getCaptcha(request);
        captcha.write(response.getOutputStream());
    }

    /**
     * 用户注册页面
     *
     * @param request
     * @return
     */
    @GetMapping("/register")
    public String register(HttpServletRequest request) {
        if (Objects.nonNull(request.getSession().getAttribute("user"))) {
            return "redirect:/";
        }

        return "/view/register";
    }

    /**
     * 用户注册方法
     *
     * @param request
     * @param userInfoDto
     * @return
     */
    @PostMapping("/userRegister")
    @ResponseBody
    public CommonResult userRegister(HttpServletRequest request, UserInfoDto userInfoDto) {
        HttpSession session = request.getSession();
        String verifyCode = userInfoDto.getVerifyCode();
        if (StrUtil.isBlank(verifyCode) || !verifyCode.equals(session.getAttribute("circleCaptchaCode"))) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("验证码不正确");
        }
        //用户名和密码是否相同
        if (userInfoDto.getUserName().equals(userInfoDto.getUserPassword())) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("用户名和密码不能相同哦");
        }

        //用户名是否已经被使用
        if (userService.count(Wrappers.<User>lambdaQuery().eq(User::getUserName, userInfoDto.getUserName())) > 0) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("该用户名已经存在哦");
        }

        User user = new User();
        BeanUtils.copyProperties(userInfoDto, user);
        user.setUserId(IdUtil.simpleUUID());
        user.setUserRegisterTime(LocalDateTime.now());
        user.setUserPassword(SecureUtil.md5(user.getUserId() + user.getUserPassword()));
        user.setUserFrozen(0);
        user.setUserPublishArticle(1);
        if (userService.save(user)) {
            return CommonResult.success("注册成功");
        }

        return CommonResult.failed("哎呀！注册失败啦，刷新页面再试一次哦~");
    }


    /**
     * 用户登陆页面
     *
     * @param request
     * @return
     */
    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        if (Objects.nonNull(request.getSession().getAttribute("user"))) {
            return "redirect:/";
        }
        model.addAttribute("referer", request.getHeader("referer"));
        return "/view/login";
    }


    /**
     * 用户登录方法
     *
     * @param request
     * @param userInfoDto
     * @return
     */
    @PostMapping("/userLogin")
    @ResponseBody
    public CommonResult userLogin(HttpServletRequest request, UserInfoDto userInfoDto) {
        HttpSession session = request.getSession();
        String verifyCode = userInfoDto.getVerifyCode();
        if (StrUtil.isBlank(verifyCode) || !verifyCode.equals(session.getAttribute("circleCaptchaCode"))) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("验证码不正确");
        }
        //用户名和密码是否相同
        if (userInfoDto.getUserName().equals(userInfoDto.getUserPassword())) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("用户名和密码不能相同哦");
        }

        //获取用户
        User userDb = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getUserName, userInfoDto.getUserName()), false);
        if (Objects.isNull(userDb)) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("用户名错误");
        }
        if (Objects.nonNull(userDb.getUserFrozen()) && userDb.getUserFrozen() == 1) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("您的账户已经被冻结，暂无法登录，请联系管理员");
        }

        if (!SecureUtil.md5(userDb.getUserId() + userInfoDto.getUserPassword()).equals(userDb.getUserPassword())) {
            session.removeAttribute("circleCaptchaCode");
            return CommonResult.failed("密码错误");
        }
        session.setAttribute("user", userDb);
        return CommonResult.success("登录成功");
    }

    /**
     * 用户退出登录
     *
     * @param request
     * @return
     */
    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().removeAttribute("user");
        return "/";
    }

    /**
     * 首页
     *
     * @return
     */
    @GetMapping("/")
    public String index(HttpServletRequest request, Model model) {

        ServletContext servletContext = request.getServletContext();

        // 查询获取首页文章类型树形目录
        List<ArticleTypeTreeVo> articleTypeList = (List<ArticleTypeTreeVo>) servletContext.getAttribute("articleTypeList");
        if (CollUtil.isEmpty(articleTypeList)) {
            articleTypeList = articleTypeService.getIndexArticleTypeList(null);
            if (CollUtil.isNotEmpty(articleTypeList)) {
                for (ArticleTypeTreeVo articleTypeTreeVo : articleTypeList) {
                    articleTypeTreeVo.setArticleTypeTreeVoList(articleTypeService.getIndexArticleTypeList(articleTypeTreeVo.getArticleTypeId()));
                }
            }
            servletContext.setAttribute("articleTypeList", articleTypeList);
        }

        // 热门文章
        List<Article> articleHotList = (List<Article>) servletContext.getAttribute("articleHotList");
        if (CollUtil.isEmpty(articleHotList)) {
            articleHotList = articleService.list(Wrappers.<Article>lambdaQuery()
                    .eq(Article::getArticleHot, 1)
                    .select(Article::getArticleId, Article::getArticleTitle)
                    .last(" limit 5"));
            servletContext.setAttribute("articleHotList", articleHotList);
        }

        // 热门标签
        List<ArticleTag> articleTagList = (List<ArticleTag>) servletContext.getAttribute("articleTagList");
        if (CollUtil.isEmpty(articleTagList)) {
            articleTagList = articleTagService.list(Wrappers.<ArticleTag>lambdaQuery()
                    .select(ArticleTag::getArticleTagId, ArticleTag::getArticleTagName));
            servletContext.setAttribute("articleTagList", articleTagList);
        }

        // 广告
        List<Ad> adIndexList = (List<Ad>) servletContext.getAttribute("adIndexList");
        if (CollUtil.isEmpty(adIndexList)) {
            AdType homeAd = adTypeService.getOne(Wrappers.<AdType>lambdaQuery()
                    .eq(AdType::getAdTypeTag, "homeAd")
                    .select(AdType::getAdTypeId));
            if (Objects.nonNull(homeAd)) {
                DateTime date = DateUtil.date();
                adIndexList = adService.list(Wrappers.<Ad>lambdaQuery()
                        .eq(Ad::getAdTypeId, homeAd.getAdTypeId())
                        .lt(Ad::getAdBeginTime, date)
                        .gt(Ad::getAdEndTime, date)
                        .select(Ad::getAdId, Ad::getAdImgUrl, Ad::getAdLinkUrl, Ad::getAdTitle)
                        .orderByAsc(Ad::getAdSort));
                System.out.println(adIndexList);
                servletContext.setAttribute("adIndexList", adIndexList);
            }
        }

        // 首页最新文章
        List<ArticleVo> indexArticleList = (List<ArticleVo>) servletContext.getAttribute("indexArticleList");
        if (CollUtil.isEmpty(indexArticleList)) {
            indexArticleList = articleService.getIndexArticleList();
            servletContext.setAttribute("indexArticleList", indexArticleList);
        }

        // 友情连接
        List<Link> linkList = (List<Link>) servletContext.getAttribute("linkList");
        if (CollUtil.isEmpty(linkList)) {
            linkList = linkService.list(Wrappers.<Link>lambdaQuery().orderByAsc(Link::getLinkSort));
            servletContext.setAttribute("linkList", linkList);
        }

        return "/view/index";

    }

    /**
     * 文章列表
     *
     * @param pageNumber
     * @return
     */
    @GetMapping("/article/list")
    public String articleListView(Integer pageNumber, String articleTitle, String articleTypeId, Model model) {
        Page<ArticleVo> articlePage = new Page<>(Objects.isNull(pageNumber) ? 1 : pageNumber, 24);
        IPage<ArticleVo> articleVoIPage = articleService.articleListView(articlePage, articleTitle, articleTypeId);

        //文章列表
        model.addAttribute("articleVoIPage", CommonPage.restPage(articleVoIPage));

        //文章分类名称
        if (StrUtil.isNotBlank(articleTypeId)) {
            ArticleType articleType = articleTypeService.getOne(Wrappers.<ArticleType>lambdaQuery().eq(ArticleType::getArticleTypeId, articleTypeId).select(ArticleType::getArticleTypeName), false);
            model.addAttribute("articleTypeName", articleType.getArticleTypeName());
            model.addAttribute("articleTypeId", articleTypeId);
        }

        return "/view/articleList";
    }


    /**
     * 获取标签对应的文章列表
     *
     * @param articleTagId
     * @param pageNumber
     * @return
     */
    @GetMapping("/tag/article/list")
    public String tagArticleList(String articleTagId, Integer pageNumber, Model model) {
        if (StrUtil.isBlank(articleTagId)) {
            return "redirect:/";
        }
        if (Objects.isNull(pageNumber) || pageNumber < 1) {
            pageNumber = 1;
        }
        Page<ArticleVo> articlePage = new Page<>(Objects.isNull(pageNumber) ? 1 : pageNumber, 24);
        IPage<ArticleVo> articleVoIPage = articleService.tagArticleList(articlePage, articleTagId);
        model.addAttribute("articleVoIPage", CommonPage.restPage(articleVoIPage));

        //获取标签类型
        ArticleTag articleTag = articleTagService.getOne(Wrappers.<ArticleTag>lambdaQuery().eq(ArticleTag::getArticleTagId, articleTagId));
        if (Objects.nonNull(articleTag)) {
            model.addAttribute("articleTagName", articleTag.getArticleTagName());
        }

        model.addAttribute("articleTagId", articleTagId);
        return "/view/tagArticleList";

    }

    /**
     * 文章
     *
     * @param articleId
     * @return
     */
    @GetMapping("/article")
    public String articleView(HttpServletRequest request, String articleId, Model model) {
        HttpSession session = request.getSession();

        ArticleVo articleVo = articleService.getArticle(articleId);
        if (Objects.isNull(articleVo)) {
            return "redirect:/";
        }

        Article article = articleService.getOne(Wrappers.<Article>lambdaQuery().eq(Article::getArticleId, articleVo.getArticleId()).select(Article::getArticleId, Article::getArticleLookNumber), false);
        //添加查看次数
        Integer articleLookNumber = article.getArticleLookNumber();
        if (Objects.isNull(articleLookNumber) || articleLookNumber < 0) {
            articleLookNumber = 0;
        }
        ++articleLookNumber;
        article.setArticleLookNumber(articleLookNumber);
        articleService.updateById(article);


        //隐藏作者用户名
        String userName = articleVo.getUserName();
        if (StrUtil.isNotBlank(userName)) {
            articleVo.setUserName(CommonUtils.getHideMiddleStr(userName));
        }

        //文章
        model.addAttribute("article", articleVo);

        //文章类型
        if (Objects.nonNull(articleVo) && StrUtil.isNotBlank(articleVo.getArticleTypeId())) {
            ArticleType articleType = articleTypeService.getOne(Wrappers.<ArticleType>lambdaQuery().eq(ArticleType::getArticleTypeId, articleVo.getArticleTypeId()).select(ArticleType::getArticleTypeName, ArticleType::getArticleTypeId), false);
            model.addAttribute("articleType", articleType);
        }

        return "/view/article";
    }

    /**
     * 获取文章评论列表
     *
     * @param articleId
     * @param pageNumber
     * @return
     */
    @PostMapping("/comment/list")
    @ResponseBody
    public CommonResult commentList(HttpServletRequest request, String articleId, Integer pageNumber) {
        if (StrUtil.isBlank(articleId)) {
            return CommonResult.failed("程序出现错误，请刷新页面重试");
        }
        if (Objects.isNull(pageNumber) || pageNumber < 1) {
            pageNumber = 1;
        }
        Page<CommentVo> commentVoPage = new Page<>(pageNumber, 5);
        IPage<CommentVo> commentVoIPage = commentService.getArticleCommentList(commentVoPage, articleId);
        commentVoIPage.getRecords().stream().forEach(commentVo -> {
            commentVo.setUserName(CommonUtils.getHideMiddleStr(commentVo.getUserName()));
        });

        //已经点过赞的评论
        HashMap<String, Long> goodCommentMap = (HashMap<String, Long>) request.getSession().getAttribute("goodCommentMap");
        if (CollUtil.isNotEmpty(goodCommentMap)) {
            List<String> commentIds = goodCommentMap.keySet().stream().collect(Collectors.toList());
            commentVoIPage.getRecords().stream().forEach(commentVo -> {
                if (commentIds.contains(commentVo.getCommentId())) {
                    commentVo.setIsGoodComment(1);
                }
            });
        }

        return CommonResult.success(CommonPage.restPage(commentVoIPage));
    }


    /**
     * 联系
     *
     * @return
     */
    @GetMapping("/contact")
    public String contact() {
        return "/view/contact";
    }

    /**
     * 捐赠
     *
     * @return
     */
    @GetMapping("/donation")
    public String donation(Model model) {
        return "/view/donation";
    }


    /**
     * 文章点赞
     *
     * @param request
     * @param articleId
     * @return
     */
    @PostMapping("/articleGood")
    @ResponseBody
    public CommonResult articleGood(HttpServletRequest request, String articleId) {
        HttpSession session = request.getSession();
        if (Objects.nonNull(session.getAttribute("articleGoodTime"))) {
            return CommonResult.failed("客官！您已经点过啦");
        }

        Article article = articleService.getById(articleId);
        Integer articleGoodNumber = article.getArticleGoodNumber();
        ++articleGoodNumber;
        article.setArticleGoodNumber(articleGoodNumber);
        if (articleService.updateById(article)) {
            session.setAttribute("articleGoodTime", true);
            return CommonResult.success("点赞成功！");
        }

        return CommonResult.failed("点赞失败");
    }


    /**
     * 收藏文章
     *
     * @param request
     * @param articleId
     * @return
     */
    @PostMapping("/articleCollection")
    @ResponseBody
    public CommonResult articleCollection(HttpServletRequest request, String articleId) {
        User user = (User) request.getSession().getAttribute("user");
        if (Objects.isNull(user)) {
            return CommonResult.failed("客官！您还没有登录呢");
        }
        return articleService.articleCollection(user, articleId);
    }

    /**
     * 搜索文章
     *
     * @param request
     * @param articleTitle
     * @return
     */
    @GetMapping("/article/search")
    public String articleSearch(HttpServletRequest request, Integer pageNumber, String articleTitle, Model model) {
        if (StrUtil.isBlank(articleTitle)) {
            return "/";
        }
        articleTitle = articleTitle.trim();
        model.addAttribute("articleTitle", articleTitle);
        if (Objects.isNull(pageNumber) || pageNumber < 1) {
            pageNumber = 1;
        }
        String ipAddr = CommonUtils.getIpAddr(request);
        ServletContext servletContext = request.getServletContext();
        ConcurrentMap<String, Long> articleSearchMap = (ConcurrentMap<String, Long>) servletContext.getAttribute("articleSearchMap");
        if (CollUtil.isEmpty(articleSearchMap) || Objects.isNull(articleSearchMap.get(ipAddr))) {
            articleSearchMap = new ConcurrentHashMap<>();
            articleSearchMap.put(ipAddr, DateUtil.currentSeconds());
        } else {
            if ((articleSearchMap.get(ipAddr) + 1 > DateUtil.currentSeconds())) {
                return "/view/searchError";
            }
        }
        //查询到的文章列表
        List<Article> articleList = new ArrayList<>();

        //拆分搜索词,查询标签
        List<Word> words = WordSegmenter.seg(articleTitle);
        List<String> titleList = words.stream().map(Word::getText).collect(Collectors.toList());
        titleList.add(articleTitle);
        List<String> articleTagIdList = articleTagService.list(Wrappers.<ArticleTag>lambdaQuery()
                .in(ArticleTag::getArticleTagName, titleList)
                .select(ArticleTag::getArticleTagId)).stream().map(ArticleTag::getArticleTagId).collect(Collectors.toList());
        List<String> articleIdList = new ArrayList<>();
        if (CollUtil.isNotEmpty(articleTagIdList)) {
            articleIdList = articleTagListService.list(Wrappers.<ArticleTagList>lambdaQuery()
                            .in(ArticleTagList::getArticleTagId, articleTagIdList)
                            .select(ArticleTagList::getArticleId)).stream()
                    .map(ArticleTagList::getArticleId).collect(Collectors.toList());

        }

        //分页查询
        IPage<Article> articlePage = new Page<>(pageNumber, 12);
        LambdaQueryWrapper<Article> queryWrapper = Wrappers.<Article>lambdaQuery()
                .like(Article::getArticleTitle, articleTitle)
                .select(Article::getArticleId,
                        Article::getArticleCoverUrl,
                        Article::getArticleCollectionNumber,
                        Article::getArticleLookNumber,
                        Article::getArticleAddTime,
                        Article::getArticleTitle);
        if (CollUtil.isNotEmpty(articleIdList)) {
            queryWrapper.or().in(Article::getArticleId, articleIdList);
        }

        IPage<Article> articleIPage = articleService.page(articlePage, queryWrapper);
        model.addAttribute("articleIPage", CommonPage.restPage(articleIPage));

        //保持搜索时间
        articleSearchMap.put(ipAddr, DateUtil.currentSeconds());
        servletContext.setAttribute("articleSearchMap", articleSearchMap);

        return "/view/articleSearch";
    }

    /**
     * 给评论点赞
     *
     * @param commentId
     * @return
     */
    @PostMapping("/goodComment")
    @ResponseBody
    public CommonResult goodComment(HttpServletRequest request, String commentId) {
        HttpSession session = request.getSession();


        if (StrUtil.isBlank(commentId)) {
            return CommonResult.failed("未获取到需要的数据，请刷新页面重试");
        }

        //一个小时只能给一个评论点赞
        HashMap<String, Long> goodCommentMap = (HashMap<String, Long>) session.getAttribute("goodCommentMap");
        if (CollUtil.isEmpty(goodCommentMap)) {
            goodCommentMap = new HashMap<>();
        } else {
            if (Objects.nonNull(goodCommentMap.get(commentId))) {
                Long goodCommentTime = goodCommentMap.get(commentId);
                if ((goodCommentTime + 3600) >= DateUtil.currentSeconds()) {
                    return CommonResult.failed("客官，这个评论您已经点过赞了哦");
                }
            }
        }

        Comment comment = commentService.getById(commentId);
        if (Objects.isNull(comment)) {
            return CommonResult.failed("点赞失败，未获取到需要的数据，请刷新页面重试");
        }
        Integer commentGoodNumber = comment.getCommentGoodNumber();
        ++commentGoodNumber;
        if (commentService.updateById(comment.setCommentGoodNumber(commentGoodNumber))) {
            goodCommentMap.put(commentId, DateUtil.currentSeconds());
            session.setAttribute("goodCommentMap", goodCommentMap);
            return CommonResult.success("点赞成功");
        }
        return CommonResult.failed("点赞失败");
    }
}
