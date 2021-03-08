package com.phonemall.controller;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import com.phonemall.domain.CartVO;
import com.phonemall.domain.CouponUserVO;
import com.phonemall.domain.Criteria;
import com.phonemall.domain.PageDTO;
import com.phonemall.domain.PurchaseVO;
import com.phonemall.domain.WishListVO;
import com.phonemall.service.CartService;
import com.phonemall.service.CouponService;
import com.phonemall.service.ProductService;
import com.phonemall.service.PurchaseService;
import com.phonemall.service.UserService;
import com.phonemall.service.WishListService;

import lombok.Setter;
import lombok.extern.log4j.Log4j;

@Controller
@Log4j
@RequestMapping("/purchase/*")
public class CartController {
	
	
	@Setter(onMethod_=@Autowired)
	private CartService cartservice;
		
	@Setter(onMethod_=@Autowired)
	private CouponService couponservice;
	
	@Setter(onMethod_=@Autowired)
	private PurchaseService purchaseservice;
	
	@PostMapping("/insertCart")
	@ResponseBody
	public ModelAndView insertCart(CartVO cartVO,Principal principal,HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
		ModelAndView mav = new ModelAndView();
		String email = principal.getName();
		long product_id = cartVO.getProduct_id();
		String color = cartVO.getProduct_color();
		int count = cartservice.readCart(email, product_id,color);
		if(count == 0) {
			cartVO.setUser_email(email);
			int total_price = cartVO.getProduct_qty() * cartVO.getProduct_price();
			cartVO.setTotal_price(total_price);
			cartservice.insertCart(cartVO);
			mav.addObject("msg","���������� �߰��Ǿ����ϴ�.");
		}
		else {
			mav.addObject("msg","�̹� ��ٱ��Ͽ� �����ϴ� ��ǰ�Դϴ�.");
		}
		redirectAttributes.addFlashAttribute("okList", "AA BB CC");
	    String referer = request.getHeader("Referer");
		mav.setView(new RedirectView(referer));
		return mav;	
	}
	
	
	@RequestMapping(value="/checkout" , method = {RequestMethod.GET, RequestMethod.POST})
	public String tocheckOut(Model model,Principal principal, int discount_value, int coupon_id) {
		String email = principal.getName();
		List<CartVO> list=cartservice.ListCart(email);
		int total_money = cartservice.sumTotalMoney(email);
		model.addAttribute("list",list);
		model.addAttribute("total_money",total_money);
		model.addAttribute("discount_value",discount_value);
		model.addAttribute("discount_result",total_money - discount_value);
		model.addAttribute("coupon_id",coupon_id);
		if(coupon_id!=0) {
			couponservice.deleteCoupon(email, coupon_id);
		}
		return "/purchase/checkout";
	}
	
	@PostMapping("/orderInsert")
	public String insertOrder(Principal principal,PurchaseVO purchaseVO) throws ParseException{
		
		//insert Today into order data
		Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date purchaseDate = sdf.parse(sdf.format(cal.getTime()));
		purchaseVO.setPurchaseDate(purchaseDate);
		
		
		//use random number to make purchase_id
		 Random rand = new Random();
	     rand.setSeed(System.currentTimeMillis());
	     Long purchase_id = (long) rand.nextInt(100000);
	     purchaseVO.setPurchase_id(purchase_id);
	     
		//insert payment method(not decided)
		purchaseVO.setPaymentMethod("Credit Cart");
		log.info(purchaseVO);
		purchaseservice.insertBuyData(purchaseVO);
		
		//transfer all cart data to order complete table
		purchaseservice.insertCompleteOrder(purchase_id);
		cartservice.deleteAllCart();
		
		return "redirect:/purchase/orderComplete";
	}
	
	@RequestMapping("orderComplete")
	public String toOrderComplete() {
		return "/purchase/orderComplete";
	}
	
	
	
	@RequestMapping("/viewCart")
	public String list(Model model,Principal principal,@ModelAttribute(value = "msg") String msg,@RequestParam(value = "couponID_input",defaultValue="0") int couponID_input,@RequestParam(value = "discount_amount",defaultValue="0") int discount_amount,RedirectAttributes rttr,@RequestParam(value = "discount_result",defaultValue="0") int discount_result) {
		log.info("goto cart");
		String email = principal.getName();
		List<CartVO> list=cartservice.ListCart(email);
		
		if(list.size()==0) {
			model.addAttribute("emptyCart","��ٱ��ϰ� ������ϴ�");
			model.addAttribute("discount_result",0);
			List<CouponUserVO> coupon_list=couponservice.getValidList(email);
			model.addAttribute("coupon_list",coupon_list);
			return "/purchase/viewCart";
		}
		int total_money = cartservice.sumTotalMoney(email);
		model.addAttribute("total_money",total_money);
		model.addAttribute("discount",discount_amount);
		model.addAttribute("couponID_input", couponID_input);
		if(discount_amount==0) {
			model.addAttribute("discount_result",total_money);
		}else {
			model.addAttribute("discount_result",discount_result);
		}
		log.info(list);
		model.addAttribute("msg",msg);
		model.addAttribute("list",list);
		List<CouponUserVO> coupon_list=couponservice.getValidList(email);
		model.addAttribute("coupon_list",coupon_list);
		return "/purchase/viewCart";
	}
	
	
	@PostMapping("/couponApply")
	public String apply(Model model,HttpServletResponse response,HttpServletRequest request,RedirectAttributes rttr,Principal principal,int price_limit,int CouponID, int discount) throws IOException {
		ModelAndView mav = new ModelAndView();
		mav.setView(new RedirectView("/purchase/viewCart"));
		int discount_amount = discount;
		String email = principal.getName();
		int total_money = cartservice.sumTotalMoney(email); 
		int discount_result = 0;
		if(total_money == 0) {
			return "redirect:/purchase/viewCart";	
		}
		if(total_money < price_limit) {
			return "redirect:/purchase/viewCart";	
		}
		else {
			if(discount_amount<=100) {
				discount_result = (int) Math.round(total_money - (total_money*(discount * 0.01)));
				discount_amount = (int) Math.round(total_money*(discount * 0.01));
			}
			
			else {
				discount_result = total_money - discount;
			}
			int couponID_input = CouponID;
			rttr.addAttribute("discount_result",discount_result);
			rttr.addAttribute("discount_amount",discount_amount);
			rttr.addAttribute("couponID_input",couponID_input);
			return "redirect:/purchase/viewCart";	
		}
	}
	
	
	@PostMapping("/deleteCart")
	@ResponseBody
	public ModelAndView deleteWishList(CartVO cartVO,Principal principal) {
		String email = principal.getName();

		cartVO.setUser_email(email);
		cartservice.deleteCart(cartVO);		
		
		ModelAndView mav = new ModelAndView();
		mav.setView(new RedirectView("/purchase/viewCart"));
		return mav;	
	}
	
	
}