package com.caxins.themis.thirdsource;


import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.caxins.themis.base.domain.ThemisApiSourceParam;
import com.caxins.themis.base.domain.ThemisApiSourceSummary;
import com.caxins.themis.base.service.IThemisApiSourceSummaryService;
import com.caxins.themis.common.api.QueryApiSource;
import com.caxins.themis.common.enums.ApiSourceEnum;
import com.caxins.themis.common.enums.CommonEnum;
import com.caxins.themis.common.enums.IntfEnum;
import com.caxins.themis.common.exception.BusinessException;
import com.caxins.themis.common.hw.WebApiInterface;
import com.caxins.themis.common.utils.ConfigUtil;
import com.caxins.themis.common.utils.DateOperation;
import com.caxins.themis.common.utils.HttpAccessServletUtil;
import com.caxins.themis.common.utils.JacksonUtil;
import com.caxins.themis.sjt.domain.SjtCourtAnnSearch;
import com.caxins.themis.sjt.service.ISjtCourtAnnSearchService;
import com.caxins.themis.tool.rulz.ObjectIsNullUtil;

@Component
public class SjtCourtAnnSearchSource {
	private static Logger logger = LoggerFactory.getLogger(SjtCourtAnnDetailSource.class);

	@Autowired
	private ISjtCourtAnnSearchService sjtCourtAnnSearchService;
	@Autowired
	private IThemisApiSourceSummaryService themisApiSourceSummaryService;
	@Autowired
	private QueryApiSource queryApiSource;
	/**
	 * 法院公告检索逻辑处理
	 * @param sjtCourtAnnSearch
	 * @return
	 */
	public Map<String, Object>sjtCourtAnnSearchHandle(SjtCourtAnnSearch sjtCourtAnnSearch){
		SjtCourtAnnSearch courtAnnSearch= new SjtCourtAnnSearch();//用于设置入库的
		Map<String, Object> result = new HashMap<String, Object>();
		//验证是否已查询
		SjtCourtAnnSearch annSearch=new SjtCourtAnnSearch();
		annSearch.setIdNo(sjtCourtAnnSearch.getIdNo());//身份证号
		annSearch.setIdTyp(sjtCourtAnnSearch.getIdTyp());
		annSearch.setCustName(sjtCourtAnnSearch.getCustName());
		annSearch.setEntityName(sjtCourtAnnSearch.getEntityName());
		annSearch.setCourtSearchActv(CommonEnum.Q_ACTIVE.getCode());
		annSearch.setCloneFlag(CommonEnum.CLONE_N.getCode());
		//最新时间，结果唯一一笔数据
		List <SjtCourtAnnSearch> searchCheckList=null;
		//SjtCourtAnnSearch search=new SjtCourtAnnSearch();

		searchCheckList=sjtCourtAnnSearchService.selectoneByObject(annSearch);//一条业务数据,添加了主体名称查询条件(精确)
		//searchCheckList=sjtCourtAnnSearchService.selectByObject(annSearch);//一条业务数据,添加了主体名称查询条件(精确)

		if(!ObjectIsNullUtil.isNullOrEmpty(searchCheckList)){//查询结果不为空，即数据库中存在该笔业务数据
			boolean flag = false;
			flag = sjtCourtAnnSearchService.clone(sjtCourtAnnSearch,courtAnnSearch,searchCheckList,result);
			if(flag){
				BigDecimal updateThemisSeq = searchCheckList.get(0).getThemisSeq();
				Map<String,Object> countMap = sjtCourtAnnSearchService.updateAllUtil(updateThemisSeq);
				int fyggCount = Integer.valueOf(countMap.get("fyggNum").toString());
				int cpwsCount = Integer.valueOf(countMap.get("cpwsNum").toString());
				int zxggCount = Integer.valueOf(countMap.get("zxggNum").toString());
				int otherInfoCount = Integer.valueOf(countMap.get("otherInfoNum").toString());

				logger.info("【法海法院公告检索】法院公告成功更新失效数据条数:"+fyggCount);
				logger.info("【法海法院公告检索】裁判文书成功更新失效数据条数:"+cpwsCount);
				logger.info("【法海法院公告检索】执行公告成功更新失效数据条数:"+zxggCount);
				logger.info("【法海法院公告检索】其他五类信息成功更新失效数据条数:"+otherInfoCount);

				logger.info("法院公告本地数据失效，调用第三方重新查询");
				result = callApi(sjtCourtAnnSearch,result);
			}else{
				result.put("status","success");
				result.put("msg","法院公告克隆数据成功");
			}
		}else{
			result = callApi(sjtCourtAnnSearch,result);
		}
	return result;
	}
	public Map<String,Object> queryFromSjtThirdSource(SjtCourtAnnSearch sjtCourtAnnSearch,Map<String,Object> result){
		logger.info("开始调用数据堂接口!");
		//查询结果为空，即数据库中无数据，是第一次查询，则调用接口并将数据入库
		String strparam="entityName="+sjtCourtAnnSearch.getEntityName()+"&matchType="+sjtCourtAnnSearch.getMatchType();//请求参数+","+sjtJudDocSearch.getMatchType()
		String strUrl="court";
		try {
			HashMap <String, Object> retMap=(HashMap<String, Object>) WebApiInterface.sendStr(strparam,strUrl);
			saveSjtCourtAnnSearch(retMap,sjtCourtAnnSearch);
			result.put("status", "success");
		} catch (Exception e) {
			logger.error("法院公告摘要队列处理数据出现异常....themis_seq="+sjtCourtAnnSearch.getThemisSeq(),e);
		}
		insertThemisApiSourceSummary(sjtCourtAnnSearch,ApiSourceEnum.SJT_SOURCE_NAME.getCode());
		return result;

	}



	/**
	 * 保存数据入库
	 */
	@SuppressWarnings("unchecked")
	public void saveSjtCourtAnnSearch(HashMap <String, Object> retMap,SjtCourtAnnSearch annSearch){
		String resCode=(String) retMap.get("resCode");
		String orderNo=(String) retMap.get("orderNo");
		HashMap<String, Object> dataList=null;
		if("0000".equals(resCode)){//查询返回成功
			try{
				dataList=(HashMap<String, Object>) retMap.get("data");
			}
			catch(Exception e){//查询成功dataList无数据，不是list类型
				annSearch.setDataId("N");
				annSearch.setResCode(resCode);
				annSearch.setOrderNo(orderNo);
				annSearch.setCourtSearchActv(CommonEnum.Q_ACTIVE.getCode());//生效
				annSearch.setCloneFlag(CommonEnum.CLONE_N.getCode());//非克隆数据
				sjtCourtAnnSearchService.insertSelective(annSearch);
				return;
			}
			//查询成功dataList有数据
			int count=(Integer) dataList.get("count");
			List<Map<String, Object>> list=(List<Map<String, Object>>) dataList.get("list");
			annSearch.setResCode(resCode);
			annSearch.setOrderNo(orderNo);
			annSearch.setCourtSearchActv(CommonEnum.Q_ACTIVE.getCode());//生效
			annSearch.setCloneFlag(CommonEnum.CLONE_N.getCode());//非克隆数据
			for(int i=0;i<count;i++){
				HashMap <String, Object> map=(HashMap<String, Object> ) list.get(i);
				String id=(String) map.get("id");
				String content=(String) map.get("content");
				if(content.length()>8000){
					content=content.substring(0,8000);
				}
				annSearch.setDataId(id);//文书id
				annSearch.setDataContent(content);
				annSearch.setDataCount(count+"");
				sjtCourtAnnSearchService.insertSelective(annSearch);
			}
		}
		else{
			//查询失败处理
			String status=(String) retMap.get("status");
			String message=(String) retMap.get("message");
			logger.error("status:"+status+",message:"+message);
			annSearch.setResCode(status);
			annSearch.setDataId("F");
			annSearch.setCourtSearchActv(CommonEnum.Q_ACTIVE.getCode());//生效
			annSearch.setCloneFlag(CommonEnum.CLONE_N.getCode());//非克隆数据
			sjtCourtAnnSearchService.insertSelective(annSearch);
		}
	}
	/**调用法海法院公告摘要接口*/
	@SuppressWarnings("unchecked")
	public Map<String,Object> queryFromFahaiThirdSource(SjtCourtAnnSearch sjtCourtAnnSearch,Map<String,Object> result)throws BusinessException{
		ConfigUtil configUtil = new ConfigUtil();
		String faHaiCourtAnnSearchURL = configUtil.getParamSecondValueOfKeyRetString("FaHaiPersonalAccurateKeywordInquiry");//请求地址
		String authCode = configUtil.getParamSecondValueOfKeyRetString("AuthCode");//授权码

		String pname = sjtCourtAnnSearch.getCustName();
		String idcardNo = sjtCourtAnnSearch.getIdNo();

		String params = null;//请求参数
		try {
			params = "authCode="+authCode+"&pname="+ URLEncoder.encode(pname,"UTF-8")+"&idcardNo="+idcardNo;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		logger.info("【法海法院公告检索】请求地址："+faHaiCourtAnnSearchURL+"?"+params);
		String resultStr = HttpAccessServletUtil.NewSendGet(faHaiCourtAnnSearchURL,params);
		//String resultStr="{\"pageNo\":1,\"range\":10,\"searchSeconds\":0.189,\"count\":44,\"entryList\":[{\"sortTime\":1477324800000,\"body\":\"...济南市中级人民法院 姜顺吉...\",\"dataType\":\"shixin\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月25日\",\"matchRatio\":0.99,\"entryId\":\"c20163701zhi794_t20161025_pjiangshunji\"},{\"sortTime\":1477324800000,\"body\":\"...0****021X 姜顺吉...\",\"dataType\":\"zxgg\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月25日\",\"matchRatio\":0.99,\"entryId\":\"c20163701zhi794_t20161025_pjiangshunji\"},{\"sortTime\":1476028800000,\"body\":\"...南市长清区人民法院 姜顺吉...\",\"dataType\":\"fygg\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月10日\",\"matchRatio\":0.99,\"entryId\":\"c2016370113zhi1821_t20161010_pjiangshunji\"},{\"sortTime\":1476028800000,\"body\":\"...0****021X 姜顺吉...\",\"dataType\":\"wdhmd\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月10日\",\"matchRatio\":0.99,\"entryId\":\"c2016370113zhi1821_t20161010_pjiangshunji\"},{\"sortTime\":1475942400000,\"body\":\"...南市长清区人民法院 姜顺吉...\",\"dataType\":\"bgt\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月09日\",\"matchRatio\":0.99,\"entryId\":\"c2016370113zhi1801_t20161009_pjiangshunji\"},{\"sortTime\":1475942400000,\"body\":\"...0****021X 姜顺吉...\",\"dataType\":\"ktgg\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月09日\",\"matchRatio\":0.99,\"entryId\":\"c2016370113zhi1801_t20161009_pjiangshunji\"},{\"sortTime\":1474387200000,\"body\":\"...级人民法院 0.0 姜顺吉...\",\"dataType\":\"cpws\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年09月21日\",\"partyId\":\"c201637minzhong1593_pjiangshunji_rt_109\",\"matchRatio\":0.4,\"entryId\":\"c201637minzhong1593\"},{\"sortTime\":1471708800000,\"body\":\"...区人民法院 0.0 姜顺吉...\",\"dataType\":\"cpws\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年08月21日\",\"partyId\":\"c2016370191zhi134_pjiangshunji_rt_109\",\"matchRatio\":0.4,\"entryId\":\"c2016370191zhi134\"},{\"sortTime\":1464883200000,\"body\":\"...区人民法院 0.0 姜顺吉...\",\"dataType\":\"ajlc\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年06月03日\",\"partyId\":\"c2016370113minchu94_pjiangshunji_rt_109\",\"matchRatio\":0.4,\"entryId\":\"c2016370113minchu94\"},{\"sortTime\":1463500800000,\"body\":\"...4000000.0 姜顺吉...\",\"dataType\":\"fygg\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年05月18日\",\"partyId\":\"c2016370113minchu95_pjiangshunji_rt_109\",\"matchRatio\":0.4,\"entryId\":\"c2016370113minchu95\"},{\"sortTime\":1477324800000,\"body\":\"...济南市中级人民法院 姜顺吉...\",\"dataType\":\"shixin\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月25日\",\"matchRatio\":0.99,\"entryId\":\"c20163701zhi794_t20161025_pjiangshunji\"},{\"sortTime\":1477324800000,\"body\":\"...济南市中级人民法院 姜顺吉...\",\"dataType\":\"ajlc\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月25日\",\"matchRatio\":0.99,\"entryId\":\"c20163701zhi794_t20161025_pjiangshunji\"},{\"sortTime\":1477324800000,\"body\":\"...济南市中级人民法院 姜顺吉...\",\"dataType\":\"shixin\",\"title\":\"姜顺吉\",\"sortTimeString\":\"2016年10月25日\",\"matchRatio\":0.99,\"entryId\":\"c20163701zhi794_t20161025_pjiangshunji\"}],\"code\":\"s\"}";

		logger.info("【法海法院公告检索】返回json数据："+resultStr);
		/**解析法海返回的json数据*/
		Long date = DateOperation.currentTimeMills();

		SjtCourtAnnSearch faHaiCourtAnnFromThird = new SjtCourtAnnSearch();
		faHaiCourtAnnFromThird.setQueryDt(DateOperation.convertToDateStr1(date));
		faHaiCourtAnnFromThird.setThemisSeq(sjtCourtAnnSearch.getThemisSeq());
		faHaiCourtAnnFromThird.setOrgCde(sjtCourtAnnSearch.getOrgCde());
		faHaiCourtAnnFromThird.setRoleTyp(sjtCourtAnnSearch.getRoleTyp());
		faHaiCourtAnnFromThird.setIdTyp(sjtCourtAnnSearch.getIdTyp());
		faHaiCourtAnnFromThird.setIdNo(sjtCourtAnnSearch.getIdNo());
		faHaiCourtAnnFromThird.setPrdId(sjtCourtAnnSearch.getPrdId());
		faHaiCourtAnnFromThird.setRuleId(sjtCourtAnnSearch.getRuleId());
		faHaiCourtAnnFromThird.setApplCde(sjtCourtAnnSearch.getApplCde());
		faHaiCourtAnnFromThird.setCustName(sjtCourtAnnSearch.getCustName());
		faHaiCourtAnnFromThird.setEntityName(sjtCourtAnnSearch.getEntityName());
		faHaiCourtAnnFromThird.setQueryUsr(sjtCourtAnnSearch.getQueryUsr());

		if(!ObjectIsNullUtil.isNullOrEmpty(resultStr)){
			Map<String,Object> resDataMap = new HashMap<String, Object>();
			resDataMap = JacksonUtil.jsonToMap(resultStr);

			//判断法海法院公告返回状态码是否成功
			if(resDataMap.containsKey("code") && "s".equals(resDataMap.get("code").toString())){
				//判断返回数据是否为空
				if(!ObjectIsNullUtil.isNullOrEmpty(resDataMap.get("entryList"))){
					faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
					faHaiCourtAnnFromThird.setCourtSearchActv(CommonEnum.Q_ACTIVE.getCode());
					faHaiCourtAnnFromThird.setResCode("0000");
					//faHaiCourtAnnFromThird.setDataCount(resDataMap.get("count").toString());
					faHaiCourtAnnFromThird.setResMsg("查询成功有数据");
					String entryListStr = resDataMap.get("entryList").toString();
					faHaiCourtAnnFromThird.setEntryList(entryListStr);
					//解析entryList
					List<Map<String,Object>> resDataList = (List<Map<String, Object>>) resDataMap.get("entryList");
					Map<String,Object> paramMap = new HashMap<String, Object>();
					paramMap.put("themisSeq",faHaiCourtAnnFromThird.getThemisSeq());
					paramMap.put("orgCde",faHaiCourtAnnFromThird.getOrgCde());
					paramMap.put("prdId",faHaiCourtAnnFromThird.getPrdId());
					paramMap.put("ruleId",faHaiCourtAnnFromThird.getRuleId());
					paramMap.put("applCde",faHaiCourtAnnFromThird.getApplCde());
					paramMap.put("roleTyp",faHaiCourtAnnFromThird.getRoleTyp());
					paramMap.put("idTyp",faHaiCourtAnnFromThird.getIdTyp());
					paramMap.put("idNo",faHaiCourtAnnFromThird.getIdNo());
					paramMap.put("custName",faHaiCourtAnnFromThird.getCustName());
					paramMap.put("queryDt",faHaiCourtAnnFromThird.getQueryDt());
					paramMap.put("queryUsr",faHaiCourtAnnFromThird.getQueryUsr());
					paramMap.put("activeFlag",faHaiCourtAnnFromThird.getCourtSearchActv());
					paramMap.put("cloneFlag",faHaiCourtAnnFromThird.getCloneFlag());
					paramMap.put("entityName",faHaiCourtAnnFromThird.getEntityName());
					paramMap.put("resCode",faHaiCourtAnnFromThird.getResCode());
					paramMap.put("resMsg",faHaiCourtAnnFromThird.getResMsg());
					paramMap.put("entryList",faHaiCourtAnnFromThird.getEntryList());
					Map<String,Object> amountMap = sjtCourtAnnSearchService.insertAllUtil(paramMap,resDataList);//处理多条数据时添加事务
					int fyggCount = Integer.valueOf(amountMap.get("fyggNum").toString());
					int cpwsCount = Integer.valueOf(amountMap.get("cpwsNum").toString());
					int zxggCount = Integer.valueOf(amountMap.get("zxggNum").toString());
					int shixinCount = Integer.valueOf(amountMap.get("shixinNum").toString());
					int wdhmdCount = Integer.valueOf(amountMap.get("wdhmdNum").toString());
					int bgtCount = Integer.valueOf(amountMap.get("bgtNum").toString());
					int ktggCount = Integer.valueOf(amountMap.get("ktggNum").toString());
					int ajlcCount = Integer.valueOf(amountMap.get("ajlcNum").toString());
					logger.info("【法海法院公告检索】法院公告成功入库数据条数:"+fyggCount);
					logger.info("【法海法院公告检索】裁判文书成功入库数据条数:"+cpwsCount);
					logger.info("【法海法院公告检索】执行公告成功入库数据条数:"+zxggCount);
					logger.info("【法海法院公告检索】失信公告成功入库数据条数:"+shixinCount);
					logger.info("【法海法院公告检索】网贷黑名单成功入库数据条数:"+wdhmdCount);
					logger.info("【法海法院公告检索】曝光台成功入库数据条数:"+bgtCount);
					logger.info("【法海法院公告检索】开庭公告成功入库数据条数:"+ktggCount);
					logger.info("【法海法院公告检索】案件流程成功入库数据条数:"+ajlcCount);
					if(fyggCount > 0){
						result.put("status","success");
						result.put("msg","【法海法院公告检索】查询成功有数据，数据入库成功");
					}else{
						result.put("status", "success");
						result.put("msg","【法海法院公告检索】查询成功无法院公告数据，入库成功");
					}
				}else{
					faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
					faHaiCourtAnnFromThird.setCourtSearchActv(CommonEnum.Q_ACTIVE.getCode());
					faHaiCourtAnnFromThird.setResCode("0000");
					faHaiCourtAnnFromThird.setDataCount(resDataMap.get("count").toString());
					faHaiCourtAnnFromThird.setDataId("F");
					faHaiCourtAnnFromThird.setDataContent("");
					faHaiCourtAnnFromThird.setResMsg("查询无数据");
					faHaiCourtAnnFromThird.setEntryList("查询无数据");

					try{
						sjtCourtAnnSearchService.insertSelective(faHaiCourtAnnFromThird);
						logger.info("【法海法院公告检索】查询无数据，入库成功");
					}catch(Exception e){
						logger.error("【法海法院公告检索】查询无数据,数据入库异常,themis流水号："+faHaiCourtAnnFromThird.getThemisSeq());
						throw new BusinessException(e);
					}

					result.put("status", "success");
					result.put("msg","【法海法院公告检索】查询无数据，入库成功");
				}
			}else{
				logger.info("【法海法院公告检索】调用接口成功，查询数据失败");
				faHaiCourtAnnFromThird.setCourtSearchActv(CommonEnum.Q_INACTIVE.getCode());
				faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
				faHaiCourtAnnFromThird.setDataId("F");
				faHaiCourtAnnFromThird.setDataContent("");
				faHaiCourtAnnFromThird.setResCode("9999");
				faHaiCourtAnnFromThird.setResMsg("查询失败");
				try{
					sjtCourtAnnSearchService.insertSelective(faHaiCourtAnnFromThird);
					logger.info("【法海法院公告检索】调用接口成功，查询数据失败");
				}catch(Exception e){
					logger.error("【法海法院公告检索】调用接口成功，查询数据失败,入库异常,themis流水号："+faHaiCourtAnnFromThird.getThemisSeq());
					throw new BusinessException(e);
				}
				result.put("status","false");
				result.put("msg","【法海法院公告检索】调用接口成功，查询数据失败");
			}
		}else{
			logger.info("【法海法院公告检索】查询接口失败");
			faHaiCourtAnnFromThird.setCourtSearchActv(CommonEnum.Q_INACTIVE.getCode());
			faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
			result.put("status","false");
			result.put("msg","【法海法院公告检索】查询接口失败");
		}

		insertThemisApiSourceSummary(faHaiCourtAnnFromThird, ApiSourceEnum.FAHAI_SOURCE_NAME.getCode());
		return result;

	}
	//数据源调用
	private Map<String,Object> callApi(SjtCourtAnnSearch courtAnnSearch,Map<String,Object> result){
		ThemisApiSourceParam source = queryApiSource.apiSource(IntfEnum.SJT_COURT_ANN_SEARCH.getCode());
		if(!ObjectIsNullUtil.isNullOrEmpty(source)) {
			if (ApiSourceEnum.FAHAI_SOURCE_NAME.getCode().equals(source.getSourceEnname())) {
				result = queryFromFahaiThirdSource(courtAnnSearch, result);
			} else if (ApiSourceEnum.SJT_SOURCE_NAME.getCode().equals(source.getSourceEnname())) {
				result = queryFromSjtThirdSource(courtAnnSearch, result);
			} else {
				double num = 1 + Math.random() * 100;
				if (num > 50) {
					result = queryFromFahaiThirdSource(courtAnnSearch, result);
				} else {
					result = queryFromSjtThirdSource(courtAnnSearch, result);
				}
			}
		}else{
			logger.info("数据源选择类中没有返回信息，不调用接口，直接入库");
			courtAnnSearch.setResCode(ApiSourceEnum.NULL_SOURCE_MESSAGE.getCode());
			courtAnnSearch.setCloneFlag(CommonEnum.CLONE_N.getCode());
			courtAnnSearch.setDataId("F");
			courtAnnSearch.setCourtSearchActv(CommonEnum.Q_INACTIVE.getCode());
			long date = DateOperation.currentTimeMills();
			courtAnnSearch.setQueryDt(DateOperation.convertToDateStr1(date));
			sjtCourtAnnSearchService.insertSelective(courtAnnSearch);
			result.put("status","false");
		}
		return result;
	}


	/**
	 * @Description(功能描述)    :  数据源摘要记录
	 */
	public void insertThemisApiSourceSummary(SjtCourtAnnSearch sjtSource, String source){
		logger.info("开始记录多源接口调用摘要。。");
		ThemisApiSourceSummary api = new ThemisApiSourceSummary();
		api.setThemisSeq(sjtSource.getThemisSeq());
		api.setPrdId(sjtSource.getPrdId());
		api.setApplCde(sjtSource.getApplCde());
		api.setApiResultSts(sjtSource.getCourtSearchActv());
		api.setApiNo(IntfEnum.SJT_COURT_ANN_SEARCH.getCode());
		api.setApiEnname(source);
		api.setCloneFlag(sjtSource.getCloneFlag());
		api.setOrgCde(sjtSource.getOrgCde());
		api.setQueryUsr(sjtSource.getQueryUsr());
		api.setCreateTime(sjtSource.getQueryDt());
		try{
			themisApiSourceSummaryService.insert(api);
		}catch (Exception e) {
			logger.error("法院公告摘要多源摘要记录失败。themisSeq="+sjtSource.getThemisSeq()+";source:"+source);
		}
	}

}
