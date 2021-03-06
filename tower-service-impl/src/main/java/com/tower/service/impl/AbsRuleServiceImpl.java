package com.tower.service.impl;

import com.alibaba.dubbo.rpc.RpcContext;
import com.tower.service.IService;
import com.tower.service.domain.IResult;
import com.tower.service.log.Logger;
import com.tower.service.log.LoggerFactory;
import com.tower.service.rule.IEngine;
import com.tower.service.rule.impl.TowerDroolsEngine;

public abstract class AbsRuleServiceImpl<T extends IResult> extends TowerDroolsEngine implements IService<T>,IEngine {
    
    protected static ServiceConfig config = new ServiceConfig();
    
	/**
	 * Logger for this class
	 */
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

	public AbsRuleServiceImpl(){
	}
	
	protected static RpcContext context = RpcContext.getContext();
    @Override
    public String getRemoteHost() {
        return context.getRemoteHost();
    }

	public String hello(String name){
		if (logger.isInfoEnabled()) {
			logger.info("hello(String name={}) - start", name); //$NON-NLS-1$
		}

		String returnString = "hello," + name;

		if (logger.isInfoEnabled()) {
			logger.info("hello(String name={}) - end - return value={}", name, returnString); //$NON-NLS-1$
		}
		return returnString;
	}
}
