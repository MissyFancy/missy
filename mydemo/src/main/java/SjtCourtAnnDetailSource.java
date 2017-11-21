package com.caxins.themis.thirdsource;



import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.caxins.themis.base.domain.ThemisApiSourceParam;
import com.caxins.themis.base.domain.ThemisApiSourceSummary;
import com.caxins.themis.base.service.IThemisApiSourceSummaryService;
import com.caxins.themis.common.api.QueryApiSource;
import com.caxins.themis.common.enums.ApiSourceEnum;
import com.caxins.themis.common.enums.IntfEnum;
import com.caxins.themis.common.exception.BusinessException;
import com.caxins.themis.common.utils.ConfigUtil;
import com.caxins.themis.common.utils.HttpAccessServletUtil;
import com.caxins.themis.common.utils.JacksonUtil;
import com.caxins.themis.sjt.domain.SjtCourtAnnSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.caxins.themis.common.enums.CommonEnum;
import com.caxins.themis.common.hw.WebApiInterface;
import com.caxins.themis.common.utils.DateOperation;
import com.caxins.themis.sjt.domain.SjtCourtAnnDetail;
import com.caxins.themis.sjt.service.ISjtCourtAnnDetailService;
import com.caxins.themis.tool.rulz.ObjectIsNullUtil;

@Component
public class SjtCourtAnnDetailSource {
	private static Logger logger = LoggerFactory.getLogger(SjtCourtAnnDetailSource.class);

	@Autowired
	private ISjtCourtAnnDetailService sjtCourtAnnDetailService;
	@Autowired
	private IThemisApiSourceSummaryService themisApiSourceSummaryService;
	@Autowired
	private QueryApiSource queryApiSource;
	/**
	 * 法院公告详情处理逻辑
	 * @param sjtCourtAnnDetail
	 * @return
	 */
	public Map<String, Object> SjtCourtAnnDetailHandle(SjtCourtAnnDetail sjtCourtAnnDetail){
		//SjtCourtAnnDetail courtAnnDetail=new SjtCourtAnnDetail();
		Map<String, Object> result = new HashMap<String, Object>();
		//验证是否已查询
		SjtCourtAnnDetail annDetail=new SjtCourtAnnDetail();
		annDetail.setIdNo(sjtCourtAnnDetail.getIdNo());
		annDetail.setIdTyp(sjtCourtAnnDetail.getIdTyp());
		annDetail.setCustName(sjtCourtAnnDetail.getCustName());
		annDetail.setCountAnnId(sjtCourtAnnDetail.getCountAnnId());
		annDetail.setCourtDetailActv(CommonEnum.Q_ACTIVE.getCode());
		annDetail.setCloneFlag(CommonEnum.CLONE_N.getCode());//生效非克隆
		SjtCourtAnnDetail detailCheck=null;
		detailCheck=sjtCourtAnnDetailService.selectoneByObject(annDetail);
		if(!ObjectIsNullUtil.isNullOrEmpty(detailCheck)){//查询结果不为空
			Long dt=DateOperation.convertToTime2(detailCheck.getQueryDt());
			int days=DateOperation.compareDaysByLong(DateOperation.currentTimeMills(), dt);
			if(days<30&&("s".equals(detailCheck.getResCode()) || "0000".equals(detailCheck.getResCode()))){//未失效且返回状态成功，则克隆数据
				sjtCourtAnnDetail.setDataAnnouncer(detailCheck.getDataAnnouncer());
				sjtCourtAnnDetail.setDataContent(detailCheck.getDataContent());
				sjtCourtAnnDetail.setDataLitigant(detailCheck.getDataLitigant());
				sjtCourtAnnDetail.setDataPublishdate(detailCheck.getDataPublishdate());
				sjtCourtAnnDetail.setDataType(detailCheck.getDataType());
				sjtCourtAnnDetail.setOrderNo(detailCheck.getOrderNo());
				sjtCourtAnnDetail.setResCode(detailCheck.getResCode());
				sjtCourtAnnDetail.setCourtDetailActv(CommonEnum.Q_ACTIVE.getCode());
				sjtCourtAnnDetail.setCloneFlag(CommonEnum.CLONE_Y.getCode());

				/**新增法海法院公告详情克隆*/
				sjtCourtAnnDetail.setFygg(detailCheck.getFygg());
				sjtCourtAnnDetail.setTotalCount(detailCheck.getTotalCount());
				sjtCourtAnnDetail.setCourt(detailCheck.getCourt());
				String currentDate = DateOperation.convertToDateStr1(DateOperation.currentTimeMills());//获取当前查询时间
				sjtCourtAnnDetail.setQueryDt(currentDate);
				sjtCourtAnnDetail.setResMsg(detailCheck.getResMsg());

				sjtCourtAnnDetailService.insert(sjtCourtAnnDetail);
				logger.info("克隆数据成功");
				result.put("status", "success");
				result.put("msg",sjtCourtAnnDetail);
				return result;
			}else{//超过30天则置为失效
				detailCheck.setCourtDetailActv(CommonEnum.Q_INACTIVE.getCode());
				sjtCourtAnnDetailService.updateByPrimaryKeySelective(detailCheck);
			}
			if("success".equals(result.get("status"))){
				return result;
			}else{
				callApi(sjtCourtAnnDetail,result);
			}
		}else{
			callApi(sjtCourtAnnDetail,result);
		}


		return result;
	}

	public Map<String,Object> queryFromSjtThirdSource(SjtCourtAnnDetail sjtCourtAnnDetail,Map<String,Object> result){
		logger.info("开始调用数据堂接口!");
		//查询结果为空，即数据库中没有数据，是第一次查询，则调用接口并将数据入库
		String strparam="id="+sjtCourtAnnDetail.getCountAnnId();//法院公告id
		String strUrl="courtDetail";
		try {
			HashMap <String, Object> retMap=(HashMap<String, Object>) WebApiInterface.sendStr(strparam,strUrl);
			saveSjtCourtAnnDetail(retMap,sjtCourtAnnDetail);
			result.put("status", "success");
		} catch (Exception e) {
			logger.error("法院公告详情处理数据出现异常....themis_seq="+sjtCourtAnnDetail.getThemisSeq(),e);
		}
		insertThemisApiSourceSummary(sjtCourtAnnDetail,ApiSourceEnum.SJT_SOURCE_NAME.getCode());
		return result;
	}




	//保存数据入库
	@SuppressWarnings("unchecked")
	public void saveSjtCourtAnnDetail(HashMap <String, Object>retMap,SjtCourtAnnDetail annDetail){
		String resCode=(String) retMap.get("resCode");
		String orderNo=(String) retMap.get("orderNo");
		List <HashMap<String,Object>> dataList=null;
		if("0000".equals(resCode)){
			try{
				dataList=(List<HashMap<String, Object>>)retMap.get("data");
			}
			catch(Exception e){
				//查询成功无数据
				annDetail.setCloneFlag(CommonEnum.CLONE_N.getCode());//非克隆
				annDetail.setCourtDetailActv(CommonEnum.Q_ACTIVE.getCode());//生效
				annDetail.setOrderNo(orderNo);
				annDetail.setResCode(resCode);
				sjtCourtAnnDetailService.insertSelective(annDetail);
				return ;
			}
			HashMap <String,Object> map=dataList.get(0);
			annDetail.setCountAnnId(annDetail.getCountAnnId());
			annDetail.setOrderNo(orderNo);
			annDetail.setResCode(resCode);
			annDetail.setDataAnnouncer((String)map.get("announcer"));
			String content=(String)map.get("content");
			if(content.length()>8000){
				content=content.substring(0,8000);
			}
			annDetail.setDataContent(content);
			annDetail.setDataLitigant((String)map.get("litigant"));
			annDetail.setDataPublishdate((String)map.get("publishDate"));
			annDetail.setDataType((String)map.get("type"));
			annDetail.setCloneFlag(CommonEnum.CLONE_N.getCode());//非克隆
			annDetail.setCourtDetailActv(CommonEnum.Q_ACTIVE.getCode());//生效
			sjtCourtAnnDetailService.insertSelective(annDetail);
		}
		else{//查询失败处理
			String status=(String) retMap.get("status");
			String message=(String) retMap.get("message");
			logger.error("status:"+status+",message:"+message);
			annDetail.setResCode(status);
			annDetail.setCloneFlag(CommonEnum.CLONE_N.getCode());//非克隆
			annDetail.setCourtDetailActv(CommonEnum.Q_ACTIVE.getCode());//生效
			sjtCourtAnnDetailService.insertSelective(annDetail);
		}
	}
	//调用法海法院公告详情接口
	public Map<String,Object> queryFromFahaiThirdSource(SjtCourtAnnDetail sjtCourtAnnDetail,Map<String, Object> result){
		ConfigUtil configUtil = new ConfigUtil();
		String faHaiCourtAnnDetailURL = configUtil.getParamSecondValueOfKeyRetString("FaHaiDetailedInquiry");//请求地址
		String authCode = configUtil.getParamSecondValueOfKeyRetString("AuthCode");//授权码

		String countAnnId = sjtCourtAnnDetail.getCountAnnId();//法院公告id

		String params = "authCode="+authCode+"&id=fygg:"+countAnnId;//请求参数

		logger.info("【法海法院公告详情】请求地址："+faHaiCourtAnnDetailURL+"?"+params);
		String resultStr = HttpAccessServletUtil.NewSendGet(faHaiCourtAnnDetailURL,params);

		logger.info("【法海法院公告详情】返回json数据："+resultStr);



		/**解析法海法院公告详情返回json数据*/
		Long date = DateOperation.currentTimeMills();

		SjtCourtAnnDetail faHaiCourtAnnFromThird = new SjtCourtAnnDetail();
		faHaiCourtAnnFromThird.setQueryDt(DateOperation.convertToDateStr1(date));
		faHaiCourtAnnFromThird.setThemisSeq(sjtCourtAnnDetail.getThemisSeq());
		faHaiCourtAnnFromThird.setOrgCde(sjtCourtAnnDetail.getOrgCde());
		faHaiCourtAnnFromThird.setRoleTyp(sjtCourtAnnDetail.getRoleTyp());
		faHaiCourtAnnFromThird.setIdTyp(sjtCourtAnnDetail.getIdTyp());
		faHaiCourtAnnFromThird.setIdNo(sjtCourtAnnDetail.getIdNo());
		faHaiCourtAnnFromThird.setPrdId(sjtCourtAnnDetail.getPrdId());
		faHaiCourtAnnFromThird.setRuleId(sjtCourtAnnDetail.getRuleId());
		faHaiCourtAnnFromThird.setApplCde(sjtCourtAnnDetail.getApplCde());
		faHaiCourtAnnFromThird.setCustName(sjtCourtAnnDetail.getCustName());
		faHaiCourtAnnFromThird.setQueryUsr(sjtCourtAnnDetail.getQueryUsr());
		faHaiCourtAnnFromThird.setCountAnnId(sjtCourtAnnDetail.getCountAnnId());

		if(!ObjectIsNullUtil.isNullOrEmpty(resultStr)){
			Map<String,Object> resDataMap = new HashMap<String, Object>();
			resDataMap = JacksonUtil.jsonToMap(resultStr);

			//判断法海法院公告详情返回状态码
			if(resDataMap.containsKey("code") && "s".equals(resDataMap.get("code").toString())){
				if(!ObjectIsNullUtil.isNullOrEmpty(resDataMap.get("fygg"))){
					faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
					faHaiCourtAnnFromThird.setCourtDetailActv(CommonEnum.Q_ACTIVE.getCode());
					faHaiCourtAnnFromThird.setResCode("0000");
					faHaiCourtAnnFromThird.setTotalCount(resDataMap.get("totalCount").toString());
					faHaiCourtAnnFromThird.setFygg(resDataMap.get("fygg").toString());
					faHaiCourtAnnFromThird.setResMsg(resDataMap.get("msg").toString());


					//解析fygg列表
					List<Map<String,Object>> resDataList = (List<Map<String, Object>>) resDataMap.get("fygg");
					sjtCourtAnnDetailService.insertAll(faHaiCourtAnnFromThird,resDataList);
					logger.info("【法海法院公告详情】查询成功有数据,数据入库成功");
					result.put("status","success");
					result.put("msg",resDataMap.get("msg"));
				}else{
					faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
					faHaiCourtAnnFromThird.setCourtDetailActv(CommonEnum.Q_ACTIVE.getCode());
					faHaiCourtAnnFromThird.setResCode("0000");
					faHaiCourtAnnFromThird.setTotalCount(resDataMap.get("totalCount").toString());
					faHaiCourtAnnFromThird.setFygg("查询成功无数据返回");
					faHaiCourtAnnFromThird.setResMsg(resDataMap.get("msg").toString());

					try{
						sjtCourtAnnDetailService.insertSelective(faHaiCourtAnnFromThird);
						logger.info("【法海法院公告详情】查询成功无数据，入库成功");
					}catch(Exception e){
						logger.error("【法海法院公告详情】查询成功无数据,数据入库异常");
						throw new BusinessException(e);
					}
					result.put("status","success");
					result.put("msg","【法海法院公告详情】查询成功无数据，入库成功");
				}


			}else{
				logger.info("【法海法院公告详情】调用接口成功，查询数据失败");
				faHaiCourtAnnFromThird.setCourtDetailActv(CommonEnum.Q_INACTIVE.getCode());
				faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
				faHaiCourtAnnFromThird.setResCode("9999");
				faHaiCourtAnnFromThird.setResMsg(resDataMap.get("msg").toString());
				try{
					sjtCourtAnnDetailService.insertSelective(faHaiCourtAnnFromThird);
					logger.info("【法海法院公告详情】调用接口成功，查询数据失败");
				}catch(Exception e){
					logger.error("【法海法院公告详情】调用接口成功，查询失败入库异常");
					throw new BusinessException(e);
				}
				result.put("status","false");
				result.put("msg",resDataMap.get("msg"));
			}
		}else{
			logger.info("【法海法院公告详情】查询接口失败");
			faHaiCourtAnnFromThird.setCourtDetailActv(CommonEnum.Q_INACTIVE.getCode());
			faHaiCourtAnnFromThird.setCloneFlag(CommonEnum.CLONE_N.getCode());
			result.put("status","false");
			result.put("msg","【法海法院公告详情】查询接口失败");
		}
		insertThemisApiSourceSummary(faHaiCourtAnnFromThird,ApiSourceEnum.FAHAI_SOURCE_NAME.getCode());
		return result;
	}
	//数据源调用
	private void callApi(SjtCourtAnnDetail courtAnnDetail,Map<String,Object> result){
		ThemisApiSourceParam source = queryApiSource.apiSource(IntfEnum.SJT_COURT_ANNOUNCEMENT.getCode());
		if(!ObjectIsNullUtil.isNullOrEmpty(source)){
			if(ApiSourceEnum.FAHAI_SOURCE_NAME.getCode().equals(source.getSourceEnname())){
				result = queryFromFahaiThirdSource(courtAnnDetail,result);
			}else if(ApiSourceEnum.SJT_SOURCE_NAME.getCode().equals(source.getSourceEnname())){
				result = queryFromSjtThirdSource(courtAnnDetail, result);
			}else{//随机调用接口
				double num = 1 + Math.random() * 100;
				if (num > 50) {
					result = queryFromFahaiThirdSource(courtAnnDetail, result);
				} else {
					result = queryFromSjtThirdSource(courtAnnDetail, result);
				}
			}
		}else{
			logger.info("数据源选择类中没有返回信息，不用调用接口，直接入库");
			courtAnnDetail.setResCode(ApiSourceEnum.NULL_SOURCE_MESSAGE.getCode());
			courtAnnDetail.setCloneFlag(CommonEnum.CLONE_N.getCode());
			courtAnnDetail.setCourtDetailActv(CommonEnum.Q_INACTIVE.getCode());
			long date = DateOperation.currentTimeMills();
			courtAnnDetail.setQueryDt(DateOperation.convertToDateStr1(date));
			sjtCourtAnnDetailService.insertSelective(courtAnnDetail);
			result.put("status","false");
			result.put("msg","数据源选择类中没有返回信息，不用调用接口，直接入库");

		}
	}


	/**
	 * @Description(功能描述)    :  数据源摘要记录
	 */
	public void insertThemisApiSourceSummary(SjtCourtAnnDetail sjtSource, String source){
		logger.info("开始记录多源接口调用摘要。。");
		ThemisApiSourceSummary api = new ThemisApiSourceSummary();
		api.setThemisSeq(sjtSource.getThemisSeq());
		api.setPrdId(sjtSource.getPrdId());
		api.setApplCde(sjtSource.getApplCde());
		api.setApiResultSts(sjtSource.getCourtDetailActv());
		api.setApiNo(IntfEnum.SJT_COURT_ANNOUNCEMENT.getCode());
		api.setApiEnname(source);
		api.setCloneFlag(sjtSource.getCloneFlag());
		api.setOrgCde(sjtSource.getOrgCde());
		api.setQueryUsr(sjtSource.getQueryUsr());
		api.setCreateTime(sjtSource.getQueryDt());
		try{
			themisApiSourceSummaryService.insert(api);
		}catch (Exception e) {
			logger.error("法院公告详情多源摘要记录失败。themisSeq="+sjtSource.getThemisSeq()+";source:"+source);
		}
	}
}
