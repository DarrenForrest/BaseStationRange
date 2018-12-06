package com.bonc.common.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

/**
 * 数据源Oracle须支持Oracle Spatial的空间数据计算、查询函数
 * @author Administrator
 */
public class BaseStationRangeUtil {
	public static void main(String[] args) {
		BaseStationRangeUtil gu = new BaseStationRangeUtil();
		String range=gu.getBaseStationRange(112.589653, 37.748447, 150, -60, 60, "autoams", "autoams123", "jdbc:oracle:thin:@192.168.100.123:1521:sxmbi");
		System.out.println("基站扇区坐标范围："+range);
	}
	/**
	 * 百度地图坐标点经纬度、区域半径(米)、区域起始角度、区域结束角度
     * 求取该坐标点扇形区域范围坐标集合
	 * @param lng               百度坐标经度
	 * @param lat                百度坐标纬度
	 * @param radius          区域半径(米)
	 * @param sDegree       区域起始角度
	 * @param eDegree       区域结束角度
	 * @param dsUname     数据源帐号
	 * @param dsPassword 数据源密码
	 * @param dsUrl           数据源连接串
	 * @return
	 */
	public String getBaseStationRange(double lng,double lat,double radius,double sDegree,double eDegree,String dsUname,String dsPassword,String dsUrl) {
		String range1 = "";
		try{
			range1+=lng+","+lat+",";
			double step = (eDegree - sDegree) / 10;
			//double step = ((eDegree - sDegree) / 10) || 10; //根据扇形的总夹角确定每步夹角度数，最大为10 
	        for (double i = sDegree; i < eDegree + 0.001; i += step) { //循环获取每步的圆弧上点的坐标，存入点数组 
	        	range1+=EOffsetBearing(lng,lat,radius,i,dsUname,dsPassword,dsUrl)+",";
	        }
	        range1+=lng+","+lat+",";
	        if(StringUtils.isNotBlank(range1)){
				range1 = range1.substring(0, range1.length()-1);
			}
		}catch(Exception ex){
			ex.printStackTrace();
		}
		return range1;
	}
	
	private String EOffsetBearing(double sourcelng,double sourcelat,double dist,double bearing,String dsUname,String dsPassword,String dsUrl) {
		double lngConv = getDistance(sourcelng,sourcelat,(sourcelng + 0.1),sourcelat,dsUname,dsPassword,dsUrl) * 10;  //计算1经度与原点的距离
		double latConv = getDistance(sourcelng,sourcelat,sourcelng,(sourcelat + 0.1),dsUname,dsPassword,dsUrl) * 10;  //计算1纬度与原点的距离
		double lat = dist * Math.sin((bearing+90) * Math.PI / 180) / latConv;  //正弦计算待获取的点的纬度与原点纬度差
		double lng = dist * Math.cos((bearing+90) * Math.PI / 180) / lngConv;  //余弦计算待获取的点的经度与原点经度差
		return (sourcelng - lng)+","+(sourcelat + lat);
	}
 
	private double getDistance(double sourcelng,double sourcelat,double targetlng,double targetlat,String dsUname,String dsPassword,String dsUrl){
		double distance=0;
		Connection bizConn=null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			Class.forName("oracle.jdbc.OracleDriver");
			bizConn = DriverManager.getConnection(dsUrl,dsUname,dsPassword);
			ps=bizConn.prepareStatement("select SDO_GEOM.SDO_DISTANCE(sdo_geometry( 2001,8307,SDO_POINT_TYPE("+sourcelng+","+sourcelat+", null),null, null),sdo_geometry( 2001,8307,SDO_POINT_TYPE("+targetlng+","+targetlat+", null),null, null),0.1, 'unit=M') from dual");
			rs = ps.executeQuery();
			while (rs.next()) {
				distance = Double.parseDouble(rs.getString(1));
			}
			rs.close();
			rs = null;
			ps.close();
			ps = null;
			if (bizConn != null) {
				bizConn.close();
				bizConn = null;
			}
		} catch (Exception se) {
			se.printStackTrace();
			try {
				bizConn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return distance;
	}
	
	/**
	 * 根据坐标集合获取区域面积
	 * @param range           区域坐标范围
	 * @param dsUname     数据源帐号
	 * @param dsPassword 数据源密码
	 * @param dsUrl           数据源连接串
	 */
	public String getArea(String range,String dsUname,String dsPassword,String dsUrl){
		String area = "";
		Connection bizConn=null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			Class.forName("oracle.jdbc.OracleDriver");
			bizConn = DriverManager.getConnection(dsUrl,dsUname,dsPassword);
			ps=bizConn.prepareStatement("select sdo_geom.sdo_area(sdo_geometry(2003,8307,null,MDSYS.SDO_ELEM_INFO_ARRAY(1,1003,1),MDSYS.SDO_ORDINATE_ARRAY("+range+") ),0.005) areaA from dual");
			rs = ps.executeQuery();
			while (rs.next()) {
				area = rs.getString(1);
			}
			rs.close();
			rs = null;
			ps.close();
			ps = null;
			if (bizConn != null) {
				bizConn.close();
				bizConn = null;
			}
		} catch (Exception se) {
			se.printStackTrace();
			try {
				bizConn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return area;
	}
	
	/**
	 * 根据两个不同的区域坐标集合获取公共区域分别占两个不同区域的覆盖率
	 * @param rangeA         区域A
	 * @param rangeB         区域B
	 * @param dsUname     数据源帐号
	 * @param dsPassword  数据源密码
	 * @param dsUrl            数据源连接串
	 * @return Map             key:precentA表示公共区域占区域A的覆盖率；key:precentB表示公共区域占区域B的覆盖率
	 */
	public Map<String,Object> getIntersectionAreaPrecent(String rangeA,String rangeB,String dsUname,String dsPassword,String dsUrl){
		Map<String,Object> precent = new HashMap<String,Object>();
		Connection bizConn=null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		String precentA=""; //（A intersection B）/A
		String precentB=""; //（B intersection A）/B
		try {
			Class.forName("oracle.jdbc.OracleDriver");
			bizConn = DriverManager.getConnection(dsUrl,dsUname,dsPassword);
			ps=bizConn.prepareStatement("select trunc(B.areaB/A.areaA,4)*100 res from ("
					+ "select sdo_geom.sdo_area(sdo_geometry(2003,8307,null,MDSYS.SDO_ELEM_INFO_ARRAY(1,1003,1),MDSYS.SDO_ORDINATE_ARRAY("+rangeA+") ),0.005) areaA from dual ) A,("
							+ "select sdo_geom.sdo_area(sdo_geom.sdo_intersection(sdo_geometry(2003,8307,null,MDSYS.SDO_ELEM_INFO_ARRAY(1,1003,1),MDSYS.SDO_ORDINATE_ARRAY("+rangeA+") ),"
									+ "sdo_geometry(2003,8307,null,MDSYS.SDO_ELEM_INFO_ARRAY(1,1003,1),MDSYS.SDO_ORDINATE_ARRAY("+rangeB+") ),0.005),0.005) areaB from dual) B");
			rs = ps.executeQuery();
			while (rs.next()) {
				precentA = rs.getString(1);
			}
			precent.put("precentA",precentA);
			rs.close();
			rs = null;
			ps.close();
			ps = null;
			
			ps=bizConn.prepareStatement("select trunc(B.areaB/A.areaA,4)*100 res from ("
					+ "select sdo_geom.sdo_area(sdo_geometry(2003,8307,null,MDSYS.SDO_ELEM_INFO_ARRAY(1,1003,1),MDSYS.SDO_ORDINATE_ARRAY("+rangeB+") ),0.005) areaA from dual ) A,("
							+ "select sdo_geom.sdo_area(sdo_geom.sdo_intersection(sdo_geometry(2003,8307,null,MDSYS.SDO_ELEM_INFO_ARRAY(1,1003,1),MDSYS.SDO_ORDINATE_ARRAY("+rangeB+") ),"
									+ "sdo_geometry(2003,8307,null,MDSYS.SDO_ELEM_INFO_ARRAY(1,1003,1),MDSYS.SDO_ORDINATE_ARRAY("+rangeA+") ),0.005),0.005) areaB from dual) B");
			rs = ps.executeQuery();
			while (rs.next()) {
				precentB = rs.getString(1);
			}
			precent.put("precentB",precentB);
			rs.close();
			rs = null;
			ps.close();
			ps = null;
			
			if (bizConn != null) {
				bizConn.close();
				bizConn = null;
			}
		} catch (Exception se) {
			se.printStackTrace();
			try {
				bizConn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return precent;
	}
	
	
}
