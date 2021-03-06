package com.gionee.wms.web.action.stock;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.*;

import javax.servlet.http.HttpServletResponse;

import com.gionee.wms.common.excel.excelimport.bean.ExcelData;
import com.gionee.wms.common.excel.excelimport.bean.ImportCellDesc;
import com.gionee.wms.common.excel.excelimport.userinterface.ExcelImportUtil;
import com.gionee.wms.entity.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.gionee.wms.common.ActionUtils;
import com.gionee.wms.common.OneBarcodeUtil;
import com.gionee.wms.common.WmsConstants;
import com.gionee.wms.common.WmsConstants.IndivWaresStatus;
import com.gionee.wms.common.excel.excelexport.module.ExcelModule;
import com.gionee.wms.common.excel.excelexport.userinterface.ExcelExpUtil;
import com.gionee.wms.dto.CommonAjaxResult;
import com.gionee.wms.dto.Page;
import com.gionee.wms.service.ServiceException;
import com.gionee.wms.service.basis.WarehouseService;
import com.gionee.wms.service.stock.StockService;
import com.gionee.wms.service.stock.TransferService;
import com.gionee.wms.service.wares.IndivService;
import com.gionee.wms.service.wares.WaresService;
import com.gionee.wms.web.action.CrudActionSupport;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.springframework.transaction.annotation.Transactional;

@Controller("TransferAction")
@Scope("prototype")
public class TransferAction extends CrudActionSupport<Transfer> {

	private static final long serialVersionUID = -8940733721506429911L;

	@Autowired
	private WarehouseService warehouseService;
	@Autowired
	private TransferService transferService;
	@Autowired
	private StockService stockService;
	@Autowired
	private IndivService indivService;
	@Autowired
	private WaresService waresService;

	/** 页面相关属性 **/
	private List<Transfer> transferList;
	private Long transferId;
	private Integer selectEnabled;// 是否允许页面select可用
	private Boolean editEnabled;// 是否允许页面数据编辑
	private Transfer transfer;
	private List<Warehouse> warehouseList;// 仓库列表
	private Page page = new Page();

	private TransferGoods goods;
	private List<TransferGoods> goodsList;
	private Long goodsId;
	private String barCodeImgPath;
	private Long warehouseId;
	private String indivCode;
	private Integer waresStatus;// 商品良次品状态
	private String[] indivCodes;
	private String[] skuCodes;
	private Integer[] waresStatuss;
	private Integer[] indivEnableds;
	private List<TransferPartner> transferPartnerList;// 调拨合作伙伴列表

	private Date createTimeBegin;// 起始时间
	private Date createTimeEnd;// 结束时间

	private String exports;

	private File upload;

	private String uploadContentType;
	private String uploadFileName;



	/**
	 * 查询调拨单列表
	 */
	@Override
	public String list() throws Exception {
		// 初始化页面数据
		warehouseList = warehouseService.getValidWarehouses();
		Map<String, Object> criteria = Maps.newHashMap();
		transferPartnerList = transferService.getTransferPartnerList(criteria);

		criteria.clear();
		if (transfer != null) {
			criteria.put("transferId", transfer.getTransferId());
			criteria.put("transferTo", StringUtils.defaultIfBlank(transfer.getTransferTo(), null));
			criteria.put("warehouseId", transfer.getWarehouseId());
			criteria.put("remark", StringUtils.defaultIfBlank(transfer.getRemark(), null));
			criteria.put("status", transfer.getStatus());
			criteria.put("createTime", transfer.getCreateTime());
			criteria.put("logisticNo", StringUtils.defaultIfBlank(transfer.getLogisticNo(), null));
		}

		criteria.put("createTimeBegin", createTimeBegin);
		criteria.put("createTimeEnd", createTimeEnd);
		if ("1".equals(exports)) {
			Page exportPage = new Page();
			exportPage.setStartRow(0);
			exportPage.setEndRow(10000);
			List<Map<String, String>> exportList = transferService.exportTransferList(criteria, exportPage);
			downloadTransfer(exportList);
			return null;
		} else {
			int totalRow = transferService.getTransferListTotal(criteria);
			page.setTotalRow(totalRow);
			page.calculate();
			criteria.put("page", page);
			transferList = transferService.getTransferList(criteria, page);
			return SUCCESS;
		}
	}

	/**
	 * 导出
	 * @param exportList
	 * @return
	 */
	private String downloadTransfer(List<Map<String, String>> exportList) {
		if (CollectionUtils.isEmpty(exportList)) {
			throw new ServiceException("没有数据！");
		}

		ExcelModule excelModule = new ExcelModule(exportList);
		HttpServletResponse response = ServletActionContext.getResponse();
		// 清空输出流
		response.reset();
		// 设置响应头和下载保存的文件名
		response.setHeader("content-disposition", "attachment;filename=stock_change" + System.currentTimeMillis() + ".xls");
		// 定义输出类型
		response.setContentType("APPLICATION/msexcel");
		OutputStream out = null;
		try {
			String templeteFile = ActionUtils.getProjectPath() + "/export/transfer_template.xls";
			String tempFile = ActionUtils.getProjectPath() + "/export/transfer_" + System.currentTimeMillis() + ".xls";
			File file = ExcelExpUtil.expExcel(excelModule, templeteFile, tempFile);
			out = response.getOutputStream();
			FileUtils.copyFile(file, out);
			out.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		return null;
	}

	/**
	 * 打开创建或编辑页面
	 */
	@Override
	public String input() throws Exception {
		// 初始化页面数据
		Map<String, Object> criteria = Maps.newHashMap();
		warehouseList = warehouseService.getValidWarehouses();
		transferPartnerList = transferService.getTransferPartnerList(criteria);
		if (transfer != null) {
			transfer = transferService.getTransferById(transfer.getTransferId());
		}
		editEnabled = true;
		return INPUT;
	}

	/**
	 * 编辑调拨清单
	 * @return
	 * @throws Exception
	 */
	public String inputManual() throws Exception {
		// 初始化页面数据
		warehouseList = warehouseService.getValidWarehouses();
		if (transfer != null) {
			transfer = transferService.getTransferById(transfer.getTransferId());
			// 获取调拨商品列表
			goodsList = transferService.getTransferGoodsForView(transfer.getTransferId());
		}
		return "input_manual";
	}

	/**
	 * 添加调拨单
	 */
	@Override
	public String add() throws Exception {
		try {
			transfer.setHandledBy(ActionUtils.getLoginName());
			transferService.addTransfer(transfer);
			ajaxSuccess("添加调拨单成功");
		} catch (ServiceException e) {
			logger.error(e.getMessage(), e);
			ajaxError("添加调拨单失败：" + e.getMessage());
		}
		return null;
	}

	/**
	 * 编辑调拨单
	 */
	@Override
	public String update() throws Exception {
		try {
			transferService.updateTransfer(transfer);
			ajaxSuccess("调拨单编辑成功");
		} catch (ServiceException e) {
			logger.error("调拨单编辑时出错", e);
			ajaxError("调拨单编辑失败：" + e.getMessage());
		}
		return null;
	}

	/**
	 * 删除调拨单
	 */
	@Override
	public String delete() throws Exception {
		try {
			List<Indiv> indivList = transferService.getIndivList(transfer.getTransferId());
			if (null != indivList && indivList.size() > 0) {
				ajaxError("调拨单在配货中，不能删除");
				return null;
			}
			transferService.deleteTransfer(transfer.getTransferId());
			ajaxSuccess("调拨单删除成功");
		} catch (ServiceException e) {
			logger.error("调拨单删除时出错", e);
			ajaxError("调拨单删除失败：" + e.getMessage());
		}
		return null;
	}

	/**
	 * 进入添加调拨商品页面
	 */
	public String inputGoodsNew() throws Exception {
		Validate.notNull(transferId);
		return "input_goods_new";
	}

	/**
	 * 添加调拨商品
	 */
	public String addGoods() throws Exception {
		Validate.notNull(transferId);
		Validate.notNull(goods);
		try {
			Transfer transfer = transferService.getTransferById(transferId);
			// 检查可销库存
			Stock stock = stockService.getStock(warehouseService.getWarehouse(transfer.getWarehouseId()).getWarehouseCode(), goods.getSkuId());
			if (stock == null) {
				ajaxError("请检查该商品库存信息");
			} else if (transfer.getTransType() == WmsConstants.TRANS_TYPE_NONDEFECTIVE && goods.getQuantity() > stock.getSalesQuantity()) {
				ajaxError("可销售库存不足");
			} else if (transfer.getTransType() == WmsConstants.TRANS_TYPE_DEFECTIVE && goods.getQuantity() > stock.getUnsalesQuantity()) {
				ajaxError("不可销售库存不足");
			} else {
				transferService.addTransferGoods(transfer, goods);
				ajaxSuccess("添加调拨商品成功");
			}
		} catch (ServiceException e) {
			logger.error("添加调拨商品时出错", e);
			ajaxError("添加调拨商品失败：" + e.getMessage());
		}
		return null;
	}

	/**
	 * 删除调拨商品
	 */
	public String deleteGoods() throws Exception {
		Validate.notNull(goodsId);
		try {
			transferService.deleteGoodsById(warehouseId, goodsId);
			ajaxSuccess("删除调拨商品成功");
		} catch (ServiceException e) {
			logger.error("删除调拨商品时出错", e);
			ajaxError("删除调拨商品失败：" + e.getMessage());
		}
		return null;
	}

	/**
	 * 批量打印预览购物清单
	 */
	public String printTransfer() {
		Validate.notNull(transferId);
		// 获取调拨商品列表
		transfer = transferService.getTransferById(transferId);
		goodsList = transferService.getTransferGoodsById(transferId);
		// 根据调拨批次号生成条码信息
		String barCodePath = ActionUtils.getProjectPath() + WmsConstants.BAR_CODE_TRANSER_PATH;
		String fileName = OneBarcodeUtil.generateBar(String.valueOf(transferId), barCodePath);
		if (fileName != null)
			barCodeImgPath = WmsConstants.BAR_CODE_TRANSER_PATH + fileName;
		return "print_transfer";
	}

	/**
	 * 进入调拨退货页面
	 */
	public String transBackInput() throws Exception {
		warehouseList = warehouseService.getValidWarehouses();
		return "trans_back_input";
	}

	/**
	 * 扫描商品个体
	 */
	public String scanIndiv() throws Exception {
		// Map<String, Object> params = Maps.newHashMap();
		CommonAjaxResult result = new CommonAjaxResult();
		IndivScanItem indivScan = new IndivScanItem();
		try {
			indivCode = indivCode == null ? null : indivCode.trim();

			if (waresStatus.intValue() == IndivWaresStatus.NON_DEFECTIVE.getCode()) {
				indivScan.setWaresStatus(IndivWaresStatus.NON_DEFECTIVE.getCode());
			} else {
				indivScan.setWaresStatus(IndivWaresStatus.DEFECTIVE.getCode());
			}

			Indiv indiv = indivService.getIndivByCode(indivCode);
			if (null == indiv) {
				// 没找到个体，按照sku编号来找
				Sku sku = waresService.getSkuByCode(indivCode);
				if (sku == null) {
					result.setOk(false);
					result.setMessage("无此商品信息");
				} else {
					indivScan.setIndivCode("");
					indivScan.setSkuCode(indivCode);
					indivScan.setSkuName(sku.getSkuName());
					indivScan.setIndivEnabled(WmsConstants.ENABLED_FALSE);
					indivScan.setTransferId("");
					indivScan.setWarehouseName("");
					indivScan.setTransferTo("");
					result.setOk(true);
					result.setResult(indivScan);
				}
			} else if (WmsConstants.IndivStockStatus.IN_WAREHOUSE.getCode() == indiv.getStockStatus().intValue()) {
				result.setOk(false);
				result.setMessage("商品已在库中");
			} else {
				// 获取调拨单仓库信息
				Transfer transfer = transferService.getTransferById(indiv.getOutId());
				if (transfer == null) {
					result.setOk(false);
					result.setMessage("无调拨单对应该商品");
				} else {
					indivScan.setIndivCode(indivCode);
					indivScan.setSkuCode(indiv.getSkuCode());
					indivScan.setSkuName(indiv.getSkuName());
					indivScan.setIndivEnabled(WmsConstants.ENABLED_TRUE);
					indivScan.setTransferId(String.valueOf(transfer.getTransferId()));
					indivScan.setWarehouseName(transfer.getWarehouseName());
					indivScan.setTransferTo(transfer.getTransferTo());
					result.setOk(true);
					result.setResult(indivScan);
				}
			}
		} catch (Exception e) {
			result.setOk(false);
			result.setMessage(e.getMessage());
		}
		ajaxObject(result);
		return null;
	}
	/**
	 * 进入导入订单excel页面
	 */
	public String transImport() throws Exception {
		warehouseList = warehouseService.getValidWarehouses();
		return "trans_import";
	}
	/**
	 * 进入导入调拨excel页面
	 */
	public String transGoodsImport() throws Exception {
		warehouseList = warehouseService.getValidWarehouses();
		return "trans_goods_import";
	}

	public String imp() throws Exception {
		String errorMsg = fileValidate(upload);
		if (StringUtils.isNotBlank(errorMsg)) {
			logger.error(errorMsg);
			ajaxError(errorMsg);
			return null;
		}
		try {
			List<Transfer> transferList = getTransferList(upload);
		//	checkService.addPhysicalGoodsList(checkService.getCheck(id), physicalGoodsList);
			for(Transfer trans:transferList){

				trans.setHandledBy(ActionUtils.getLoginName());
				transferService.addTransfer(trans);
			}

			ajaxSuccess("上传订单数据成功");
		} catch (Exception e) {
			logger.error("上传订单数据时出错", e);
			ajaxError("上传订单数据时出错：" + e.getMessage());
		}

		return null;
	}


	public String impGoods() throws Exception {
		String errorMsg = fileValidate(upload);
		if (StringUtils.isNotBlank(errorMsg)) {
			logger.error(errorMsg);
			ajaxError(errorMsg);
			return null;
		}
		try {
			Transfer transfer =new Transfer();
			transfer = transferService.getTransferById(transferId);
			if(transfer==null){
				throw new RuntimeException("到不到此订单号");
			}
			if(transfer.getStatus()!=1){
				throw new RuntimeException(transfer.getTransferId()+":此单号SKU不能修改");
			}

			List<TransferGoods> physicalGoodsList = getTransferGoodsList(upload);
			for(TransferGoods transferGoods:physicalGoodsList){

				Stock stock=stockService.getStock(warehouseService.getWarehouse(transfer.getWarehouseId()).getWarehouseCode(),transferGoods.getSkuId());

				if(stock==null){
					ajaxError("请检查该商品库存信息:"+transferGoods.getSkuCode());
				}else if(transfer.getTransType()==WmsConstants.TRANS_TYPE_NONDEFECTIVE){
					ajaxError("可销售库存不足:"+transferGoods.getSkuCode());
				}else if(transfer.getTransType() == WmsConstants.TRANS_TYPE_DEFECTIVE && transferGoods.getQuantity() > stock.getUnsalesQuantity()){
					ajaxError("不可销售库存不足:"+transferGoods.getSkuCode());
				}else {
					//transferGoods.setQuantity(sku.get);
					transferGoods.setTransferId(transfer.getTransferId()+"");
					transferGoods.setMeasureUnit("台");
					transferService.addTransferGoods(transfer, transferGoods);
					ajaxSuccess("添加调拨商品成功");
				}
			}


			//ajaxSuccess("上传订单数据成功");
		} catch (Exception e) {
			logger.error("上传订单数据时出错", e);
			ajaxError("上传订单数据时出错：" + e.getMessage());
		}

		return null;
	}

	private List<Transfer> getTransferList(File upload) throws Exception {
		List<Transfer> list=new ArrayList<Transfer>();
		ExcelData uploadData = null;
		uploadData = ExcelImportUtil.readExcel(WmsConstants.TANSFER_IMP_DESC, FileUtils.openInputStream(upload));

		if (uploadData == null || CollectionUtils.isEmpty(uploadData.getRepeatData())) {
			throw new RuntimeException("文件数据格式不正确");
		}

		List<Map<String, ImportCellDesc>> physicalExcelRows = uploadData.getRepeatData();
		for (Map<String, ImportCellDesc> row : physicalExcelRows) {
			Transfer transfer =new Transfer();
			Map<String, Object> criteria = Maps.newHashMap();
			try {

				transferPartnerList = transferService.getTransferPartnerList(criteria);
				warehouseList = warehouseService.getValidWarehouses();

				transfer.setWarehouseName(row.get("WAREHOUSE_NAME").getFieldValue());
				transfer.setTransferTo(row.get("TRANSFERTO").getFieldValue());
				transfer.setContact(row.get("CONSTACT").getFieldValue());
				transfer.setLogisticName(row.get("LOGISTIC_NAME").getFieldValue());
				transfer.setOrderAmount(new BigDecimal(row.get("ORDER_AMOUNT").getFieldValue()));
				transfer.setPo(row.get("PO").getFieldValue());
				transfer.setTransType(row.get("TANS_TYPE").getFieldValue().toString().equals("良品调拨") ? 0 : 1);

				transfer.setRemark(row.get("REMARK").getFieldValue());
				//transfer.setTransferTo();
				for(Warehouse warehouse:warehouseList){
					if(row.get("WAREHOUSE_NAME").getFieldValue().equals(warehouse.getWarehouseName())){
						transfer.setWarehouseId(warehouse.getId());
					}
				}

				for(TransferPartner ts:transferPartnerList){
					if(ts.getName().equals(row.get("TRANSFER_SALE").getFieldValue())){
						transfer.setTransferSale(ts.getId());
					}
					if(ts.getName().equals(row.get("TANSFER_SEND").getFieldValue())){
						transfer.setTransferSend(ts.getId());
					}
					if(ts.getName().equals(row.get("TANSFER_INVOICE").getFieldValue())){
						transfer.setTransferInvoice(ts.getId());
					}
				}
				//transfer.setTransferSale();

			} catch (Exception e) {
				throw new RuntimeException("请正确填写调拨单数据");
			}
			/*if (transferList.containsKey(physicalGoods.getSkuCode())) {
				throw new RuntimeException("商品条目存在重复");
			}*/
			//transferService.addTransfer(transfer);
			list.add(transfer);
		}
		return list;
	}
	private List<TransferGoods> getTransferGoodsList(File upload) throws Exception {
		List<TransferGoods> list=new ArrayList<TransferGoods>();
		ExcelData uploadData = null;
		uploadData = ExcelImportUtil.readExcel(WmsConstants.TANSFER_GOODS_IMP_DESC, FileUtils.openInputStream(upload));

		if (uploadData == null || CollectionUtils.isEmpty(uploadData.getRepeatData())) {
			throw new RuntimeException("文件数据格式不正确");
		}

		List<Map<String, ImportCellDesc>> physicalExcelRows = uploadData.getRepeatData();
		for (Map<String, ImportCellDesc> row : physicalExcelRows) {

			TransferGoods goods=new TransferGoods();
			Map<String, Object> criteria = Maps.newHashMap();
			try {
				///transferId
				transferPartnerList = transferService.getTransferPartnerList(criteria);
				warehouseList = warehouseService.getValidWarehouses();


				String sku_code=row.get("SKU_CODE").getFieldValue();
				String quantity=row.get("QUANTITY").getFieldValue();//数量
				String number=row.get("NUMBER").getFieldValue();//单价
				//System.out.println(StringToString(sku_code) + ":" + quantity + ":" + number);


				if(row.get("SKU_CODE").getFieldValue()!=null){
					Sku sku=waresService.getSkuByCode(StringToString(sku_code));
					goods.setSkuId(sku.getId());
					goods.setSkuCode(sku.getSkuCode());
					goods.setSkuName(sku.getSkuName());
					BigDecimal bigDecimal=new BigDecimal(number);
					goods.setUnitPrice(bigDecimal);
					goods.setQuantity(StringToInt(quantity));
					goods.setMeasureUnit(sku.getWares().getMeasureUnit());

					//goods.set

				}else{
					throw new RuntimeException("请填写正确的sku:"+row.get("SKU_CODE").getFieldValue());
				}

				//transfer.setTransferSale();

			} catch (Exception e) {
				throw new RuntimeException("请正确填写商品数据");
			}
				list.add(goods);
			/*if (transferList.containsKey(physicalGoods.getSkuCode())) {
				throw new RuntimeException("商品条目存在重复");
			}*/
			//transferService.addTransfer(transfer);
			//list.add(sku);
		}
		return list;
	}



	public static Integer StringToInt(String number){
		boolean flag=number.contains(".");
		Integer a=0;
		if(flag){
			a=Integer.parseInt(number.substring(0, number.indexOf(".")));
		}else{
			a=Integer.parseInt(number);
		}
		return a;
	}
	public static String StringToString(String number){
		boolean flag=number.contains(".");
		String a="";
		if(flag){
			a=number.substring(0, number.indexOf("."));
		}else{
			a=number;
		}
		return a;
	}



	private String fileValidate(File upload) {
		String errorMsg = "";
		if (upload == null) {
			errorMsg = "上传文件为空";
		} else if (StringUtils.isBlank(uploadContentType)
				|| !WmsConstants.EXCEL_UPLOAD_ALLOWED_TYPES.contains(uploadContentType)) {
			errorMsg = "上传文件类型不是Excel";
		} else if (upload.length() > WmsConstants.EXCEL_UPLOAD_MAXIMUM_SIZE) {
			errorMsg = "上传文件不能大于2M";
		}
		logger.info("upload file name: " + uploadFileName);
		logger.info("upload file type: " + uploadContentType);
		logger.info("upload file size: " + upload.length());
		return errorMsg;
	}



	/**
	 * 确认退回
	 */
	public String confirmBack() throws Exception {
		try {
			if (indivCodes == null || indivCodes.length == 0) {
				ajaxSuccess("请扫描退货商品");
				return null;
			}
			if (indivCodes.length != Sets.newHashSet(indivCodes).size()) {
				ajaxSuccess("扫描的串号有重复！");
				return null;
			}

			transferService.confirmBack(skuCodes, indivCodes, waresStatuss, indivEnableds, warehouseId);
			ajaxSuccess("");

		} catch (ServiceException e) {
			logger.error(e.getMessage(), e);
			ajaxError("退回失败：" + e.getMessage());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			ajaxError("系统错误");
		}
		return null;
	}

	public String cancelTransfer() throws Exception {
		try {
			transferService.cancelTransfer(transferId);
			ajaxSuccess("取消成功");
		} catch (ServiceException e) {
			logger.error("取消失败", e);
			ajaxError("取消失败：" + e.getMessage());
		}
		return null;
	}

	@Override
	public Transfer getModel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void prepareInput() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void prepareUpdate() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void prepareAdd() throws Exception {
		// TODO Auto-generated method stub

	}

	public Page getPage() {
		if (page == null) {
			page = new Page();
		}
		return page;
	}

	public List<Transfer> getTransferList() {
		return transferList;
	}

	public void setTransferList(List<Transfer> transferList) {
		this.transferList = transferList;
	}

	public Long getTransferId() {
		return transferId;
	}

	public void setTransferId(Long transferId) {
		this.transferId = transferId;
	}

	public Integer getSelectEnabled() {
		return selectEnabled;
	}

	public void setSelectEnabled(Integer selectEnabled) {
		this.selectEnabled = selectEnabled;
	}

	public Boolean getEditEnabled() {
		return editEnabled;
	}

	public void setEditEnabled(Boolean editEnabled) {
		this.editEnabled = editEnabled;
	}

	public Transfer getTransfer() {
		return transfer;
	}

	public void setTransfer(Transfer transfer) {
		this.transfer = transfer;
	}

	public List<Warehouse> getWarehouseList() {
		return warehouseList;
	}

	public void setWarehouseList(List<Warehouse> warehouseList) {
		this.warehouseList = warehouseList;
	}

	public TransferGoods getGoods() {
		return goods;
	}

	public void setGoods(TransferGoods goods) {
		this.goods = goods;
	}

	public List<TransferGoods> getGoodsList() {
		return goodsList;
	}

	public void setGoodsList(List<TransferGoods> goodsList) {
		this.goodsList = goodsList;
	}

	public Long getGoodsId() {
		return goodsId;
	}

	public void setGoodsId(Long goodsId) {
		this.goodsId = goodsId;
	}

	public String getBarCodeImgPath() {
		return barCodeImgPath;
	}

	public void setBarCodeImgPath(String barCodeImgPath) {
		this.barCodeImgPath = barCodeImgPath;
	}

	public Long getWarehouseId() {
		return warehouseId;
	}

	public void setWarehouseId(Long warehouseId) {
		this.warehouseId = warehouseId;
	}

	public String getIndivCode() {
		return indivCode;
	}

	public void setIndivCode(String indivCode) {
		this.indivCode = indivCode;
	}

	public Integer getWaresStatus() {
		return waresStatus;
	}

	public void setWaresStatus(Integer waresStatus) {
		this.waresStatus = waresStatus;
	}

	public String[] getIndivCodes() {
		return indivCodes;
	}

	public void setIndivCodes(String[] indivCodes) {
		this.indivCodes = indivCodes;
	}

	public String[] getSkuCodes() {
		return skuCodes;
	}

	public void setSkuCodes(String[] skuCodes) {
		this.skuCodes = skuCodes;
	}

	public Integer[] getWaresStatuss() {
		return waresStatuss;
	}

	public void setWaresStatuss(Integer[] waresStatuss) {
		this.waresStatuss = waresStatuss;
	}

	public Integer[] getIndivEnableds() {
		return indivEnableds;
	}

	public void setIndivEnableds(Integer[] indivEnableds) {
		this.indivEnableds = indivEnableds;
	}

	public List<TransferPartner> getTransferPartnerList() {
		return transferPartnerList;
	}

	public void setTransferPartnerList(List<TransferPartner> transferPartnerList) {
		this.transferPartnerList = transferPartnerList;
	}

	/**
	 * @return the exports
	 */
	public String getExports() {
		return exports;
	}

	/**
	 * @param exports the exports
	 */
	public void setExports(String exports) {
		this.exports = exports;
	}

	/**
	 * @return the createTimeBegin
	 */
	public Date getCreateTimeBegin() {
		return createTimeBegin;
	}

	/**
	 * @param createTimeBegin the createTimeBegin
	 */
	public void setCreateTimeBegin(Date createTimeBegin) {
		this.createTimeBegin = createTimeBegin;
	}

	/**
	 * @return the createTimeEnd
	 */
	public Date getCreateTimeEnd() {
		return createTimeEnd;
	}

	/**
	 * @param createTimeEnd the createTimeEnd
	 */
	public void setCreateTimeEnd(Date createTimeEnd) {
		this.createTimeEnd = createTimeEnd;
	}

	public File getUpload() {
		return upload;
	}

	public void setUpload(File upload) {
		this.upload = upload;
	}
	public String getUploadContentType() {
		return uploadContentType;
	}

	public String getUploadFileName() {
		return uploadFileName;
	}

	public void setUploadContentType(String uploadContentType) {
		this.uploadContentType = uploadContentType;
	}

	public void setUploadFileName(String uploadFileName) {
		this.uploadFileName = uploadFileName;
	}

	public void setWarehouseService(WarehouseService warehouseService) {
		this.warehouseService = warehouseService;
	}

	public WarehouseService getWarehouseService() {

		return warehouseService;
	}
}
