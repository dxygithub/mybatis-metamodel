package com.github.thinwonton.mybatis.metamodelgen.test.tkmapper.entity;

import java.util.Date;

/**
 * ORMEntity
 */
public abstract class ORMEntity {
    private Date createDate;
    private Date updateDate;

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }
}
