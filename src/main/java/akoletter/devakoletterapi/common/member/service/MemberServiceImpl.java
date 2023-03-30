package akoletter.devakoletterapi.common.member.service;

import akoletter.devakoletterapi.common.member.domain.request.LoginRequest;
import akoletter.devakoletterapi.common.member.domain.request.LogoutRequest;
import akoletter.devakoletterapi.common.member.domain.request.SignUpRequest;
import akoletter.devakoletterapi.common.member.domain.response.LoginResponse;
import akoletter.devakoletterapi.common.member.domain.response.SignUpResponse;
import akoletter.devakoletterapi.jpa.authority.entity.Authority;
import akoletter.devakoletterapi.jpa.membermst.entity.MemberMst;
import akoletter.devakoletterapi.jpa.membermst.repo.MemberMstRepository;
import akoletter.devakoletterapi.jpa.token.entity.Token;
import akoletter.devakoletterapi.jpa.token.repo.TokenRepository;
import akoletter.devakoletterapi.util.jwt.JwtProvider;
import akoletter.devakoletterapi.util.jwt.TokenDto;
import akoletter.devakoletterapi.util.response.Response;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Transactional
public class MemberServiceImpl implements MemberService {
  private final Response response;
  private final MemberMstRepository memberMstRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final TokenRepository tokenRepository;

  private final Integer EXP = Math.toIntExact(Duration.ofDays(14).toMillis());
//  private final Integer EXP = 1000 * 60 * 2;

  @Override
  public ResponseEntity<?> login(LoginRequest request) {
    MemberMst memberMst = memberMstRepository.findByUsrId(request.getUsrId()).orElse(null);
    if(memberMst==null){
      return response.fail("계정 정보가 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
    }
    if (!passwordEncoder.matches(request.getUsrPwd(), memberMst.getUsrPwd())){
      return response.fail("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
    }
    String accessToken = jwtProvider.createAccessToken(memberMst.getUsrId(), memberMst.getRoles());
    Token refreshToken = validRefreshToken(memberMst, memberMst.getRefreshToken());
    if(refreshToken == null || refreshToken.getExpiration() <= 0){
      refreshToken = createRefreshToken(memberMst);
      memberMst.setRefreshToken(refreshToken.getRefreshToken());
    }
    memberMstRepository.save(memberMst);
    LoginResponse loginResponse = LoginResponse.builder()
        .usrId(memberMst.getUsrId())
        .usrNm(memberMst.getUsrNm())
        .usrEmail(memberMst.getUsrEmail())
        .usrTelNo(memberMst.getUsrTelNo())
        .roles(memberMst.getRoles())
        .token(TokenDto.builder()
            .access_token(accessToken)
            .refresh_token(refreshToken.getRefreshToken())
            .expiration_ms(refreshToken.getExpiration())
            .build())
        .build();
    return response.success(loginResponse, "로그인에 성공했습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity<?> signUp(SignUpRequest request){
    MemberMst memberCheck = memberMstRepository.findByUsrId(request.getUsrId()).orElse(null);
    SignUpResponse result = new SignUpResponse();
    if(memberCheck != null || memberMstRepository.findByUsrEmail(request.getUsrEmail()).orElse(null) != null || memberMstRepository.findByUsrTelNo(request.getUsrTelNo()).orElse(null) != null){
      return response.fail("이미 존재하는 계정 정보입니다.", HttpStatus.BAD_REQUEST);
    }
    MemberMst memberMst = MemberMst.builder()
        .usrId(request.getUsrId())
        .usrPwd(passwordEncoder.encode(request.getUsrPwd()))
        .usrNm(request.getUsrNm())
        .usrEmail(request.getUsrEmail())
        .usrTelNo(request.getUsrTelNo())
        .build();
    memberMst.setRoles(Collections.singletonList(Authority.builder().member(memberMst).name("ROLE_USER").build()));
    memberMstRepository.saveAndFlush(memberMst);
    result.setSuccess("true");
    return response.success(result, "회원가입에 성공했습니다.", HttpStatus.OK);
  }

  // Refresh Token

  /**
   * Refresh 토큰을 생성한다.
   * Redis 내부에는
   * refreshToken:memberId : tokenValue
   * 형태로 저장한다.
   */

  public Token createRefreshToken(MemberMst memberMst){
    return tokenRepository.save(
        Token.builder()
            .id(memberMst.getUnqUsrId())
            .refreshToken(UUID.randomUUID().toString())
            .expiration(EXP)
            .build()
    );
  }


  public Token validRefreshToken(MemberMst memberMst, String refreshToken) {
    Token token = tokenRepository.findById(memberMst.getUnqUsrId()).orElse(null);
    // 해당유저의 Refresh 토큰 만료 : Redis에 해당 유저의 토큰이 존재하지 않음
    if (token == null || token.getRefreshToken() == null) {
      return null;
    } else {
      // 리프레시 토큰 만료일자가 얼마 남지 않았을 때 null 반환
      if(token.getExpiration() < 10) {
        return null;
      }
      // 토큰이 같은지 비교
      if(!token.getRefreshToken().equals(refreshToken)) {
        return null;
      } else {
        return token;
      }
    }
  }
  @Override
  public ResponseEntity<?> refreshAccessToken(TokenDto token) {
    String usrId = jwtProvider.getAccount(token.getAccess_token());
    MemberMst memberMst = memberMstRepository.findByUsrId(usrId).orElse(null);
    if(memberMst == null){
      return response.fail("회원정보가 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
    }
    Token refreshToken = validRefreshToken(memberMst, token.getRefresh_token());
    if (refreshToken == null) {
      return response.fail("다시 로그인해 주세요.", HttpStatus.BAD_REQUEST);
    }
    String accessToken = jwtProvider.createAccessToken(usrId, memberMst.getRoles());
    TokenDto tokenDto = TokenDto.builder()
        .access_token(accessToken)
        .refresh_token(memberMst.getRefreshToken())
        .build();
    return response.success(tokenDto, "", HttpStatus.OK);
  }

  @Override
  public ResponseEntity<?> logout(LogoutRequest request) {
    String usrId = jwtProvider.getAccount(request.getAccessToken());
    MemberMst memberMst = memberMstRepository.findByUsrId(usrId).orElse(null);
    // access token이 유효한지 확인
    if(!jwtProvider.validateToken(request.getAccessToken()) || usrId == null){
      return response.fail("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
    }
    // refresh token 존재 여부 확인
    Token refresh = tokenRepository.findById(memberMst.getUnqUsrId()).orElse(null);
    if(refresh == null){
      return response.fail("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
    }
    /** refresh token 삭제
     * 다시 로그인 하면 refresh token 재발급
     * 로그인 하지 않고 사이트를 나가게 되면
     * 1. access token이 유효하다면, 다시 서비스 이용 가능
     * 2. access token이 유효하지 않지만, refresh token이 유효하다면, access token 재발급
     */
    tokenRepository.delete(refresh);
    return response.success("로그아웃 되었습니다.");
  }

}
