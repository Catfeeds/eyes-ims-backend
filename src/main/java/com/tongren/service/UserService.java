package com.tongren.service;

import com.github.pagehelper.PageHelper;
import com.tongren.bean.CommonResult;
import com.tongren.bean.Constant;
import com.tongren.bean.Identity;
import com.tongren.mapper.DoctorMapper;
import com.tongren.pojo.User;
import com.tongren.util.MD5Util;
import com.tongren.util.TokenUtil;
import com.tongren.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class UserService extends BaseService<User> {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private DoctorMapper doctorMapper;

    /**
     * 检查用户名是否重复
     *
     * @param username
     * @return true 重复（数据库中存在）
     */
    public boolean isExist(String username) {
        User record = new User();
        record.setUsername(username);
        return super.queryOne(record) != null;
    }



    /**
     * 登录验证
     *
     * @param username
     * @param password
     * @param type
     * @return
     */
    public CommonResult login(String username, String password, String type) {

        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info("{} 用户请求登录", username);

        {
            User record = new User();
            record.setUsername(username);
            User user = this.queryOne(record);

            if (user == null) {
                return CommonResult.failure("登录失败：用户不存在");
            }
        }


        // 密码加密
        String md5Password;
        try {
            md5Password = MD5Util.generate(password);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return CommonResult.failure("MD5加密失败");
        }

        // 从数据库中取出对应的user
        User record = new User();
        record.setUsername(username);
        User targetUser = this.queryOne(record);

        // 检验密码
        if (!targetUser.getPassword().equals(md5Password)) {
            return CommonResult.failure("密码错误");
        }

        // 生成token
        CommonResult result = this.generateToken(targetUser.getId().toString(),
                Constant.TOKEN_ISSUER,
                targetUser.getUsername(),
                targetUser.getRole(),
                "/avatar/" + targetUser.getAvatar(),
                Constant.TOKEN_DURATION,
                targetUser.getDoctorId() == null ? null : targetUser.getDoctorId().toString(),
                Constant.TOKEN_API_KEY_SECRET);

        ((Identity) result.getContent()).setName(targetUser.getName());

        return result;
    }


    /**
     * 为通过登录验证的用户生成token
     *
     * @param id
     * @param issuer
     * @param username
     * @param role
     * @param avatar
     * @param duration
     * @param apiKeySecret
     * @return
     */
    public CommonResult generateToken(String id, String issuer, String username, String role, String avatar, Long
            duration, String doctorId, String apiKeySecret) {

        Identity identity = new Identity();
        identity.setId(id);
        identity.setIssuer(issuer);
        identity.setUsername(username);
        identity.setRole(role);
        identity.setDuration(duration);
        identity.setAvatar(avatar);
        identity.setDoctorId(doctorId);
        String token = TokenUtil.createToken(identity, apiKeySecret);

        // 封装返回前端(除了用户名、角色、时间戳保留，其余消去)
        identity.setToken(token);
        identity.setIssuer(null);
        return CommonResult.success("登录成功", identity);
    }




    /**
     * 查询所有会员
     *
     * @return
     */
    public List<User> queryAllMembers() {

        Example example = new Example(User.class);
        Example.Criteria criteria = example.createCriteria();

        criteria.andLike("role", "%会员%");
        return this.getMapper().selectByExample(example);
    }


    /**
     * 条件查询会员
     *
     * @param pageNow
     * @param pageSize
     * @param role
     * @param username
     * @param name
     * @param identity
     * @return
     */
    public List<User> queryUserList(Integer pageNow, Integer pageSize, String role, String username, String name, Identity identity) {

        Example example = new Example(User.class);
        Example.Criteria criteria = example.createCriteria();


        if (!Validator.checkEmpty(name)) {
            criteria.andLike(Constant.NAME, "%" + name + "%");
        }

        if (!Validator.checkEmpty(username)) {
            criteria.andLike(Constant.USERNAME, "%" + username + "%");
        }

        if (!Validator.checkEmpty(role)) {
            criteria.andLike(Constant.ROLE, "%" + role + "%");
        }

        PageHelper.startPage(pageNow, pageSize);
        return this.getMapper().selectByExample(example);
    }


    /**
     * 模糊匹配姓名，查询会员
     *
     * @param userName
     * @return
     */
    public Set<Integer> getMemberIdSetByUserNameLike(String userName) {
        return getIdSetByUserNameLikeAndRole(userName, "会员");
    }


    /**
     * 模糊匹配姓名，查询职员
     *
     * @param userName
     * @return
     */
    public Set<Integer> getEmployeeIdSetByUserNameLike(String userName) {
        return this.getIdSetByUserNameLikeAndRole(userName, "职员");
    }

    /**
     * 根据姓名和角色模糊匹配，将匹配的结果的id组成set返回
     *
     * @param name
     * @param role
     * @return
     */
    public Set<Integer> getIdSetByUserNameLikeAndRole(String name, String role) {

        Example userExample = new Example(User.class);
        Example.Criteria userCriteria = userExample.createCriteria();

        userCriteria.andLike("name", "%" + name + "%");

        if (role.equals("职员")) {
            userCriteria.andNotLike("role", "%会员%");
        } else {
            userCriteria.andLike("role", "%" + role + "%");
        }

        List<User> userList = this.getMapper().selectByExample(userExample);

        Set<Integer> userIdSet = new HashSet<>();
        for(User user : userList)
            userIdSet.add(user.getId());

        // 结果为空的话查询会出错
        if (userIdSet.size() == 0) {
            userIdSet.add(-1);
        }

        return userIdSet;
    }


    /**
     * 删除用户，并级联删除医师
     * @param user
     * @return
     */
    public Integer delete(User user) {

        Integer doctorId = user.getDoctorId();
        this.getMapper().delete(user);
        this.doctorMapper.deleteByPrimaryKey(doctorId);

        return Constant.CRUD_SUCCESS;
    }


}
