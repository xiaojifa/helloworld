package com.hzy.blog.entity;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author Fa Xiaoji 14439
 * @date 2024/4/14 16:24
 */

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class ArticleType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文章分类id
     */
    @TableId(value = "article_type_id")
    private String articleTypeId;

    /**
     * 文章分类父id
     */
    private String articleTypeParentId;

    /**
     * 文章分类名称
     */
    private String articleTypeName;

    /**
     * 文章分类排序
     */
    private Integer articleTypeSort;

    /**
     * 添加时间
     */
    private LocalDateTime articleTypeAddTime;

}
