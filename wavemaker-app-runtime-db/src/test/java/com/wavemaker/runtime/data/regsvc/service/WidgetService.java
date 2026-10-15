package com.wavemaker.runtime.data.regsvc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wavemaker.runtime.data.dao.WMGenericDao;
import com.wavemaker.runtime.data.regsvc.Widget;

@Service("svc1.WidgetService")
public class WidgetService {

    @Autowired
    @Qualifier("svc1.WidgetDao")
    private WMGenericDao<Widget, Integer> dao;

    @Transactional(value = "svc1TransactionManager")
    public Widget create(Widget widget) {
        return dao.create(widget);
    }

    @Transactional(value = "svc1TransactionManager", readOnly = true)
    public Widget get(Integer id) {
        return dao.findById(id);
    }
}
