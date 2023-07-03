package com.anabada.neighbor.club.controller;

import com.anabada.neighbor.chat.domain.Chat;
import com.anabada.neighbor.chat.service.ChattingService;
import com.anabada.neighbor.club.domain.ClubRequest;
import com.anabada.neighbor.club.domain.ClubResponse;
import com.anabada.neighbor.club.domain.ImageRequest;
import com.anabada.neighbor.club.domain.ImageResponse;
import com.anabada.neighbor.club.domain.entity.Club;
import com.anabada.neighbor.club.service.ClubService;
import com.anabada.neighbor.club.service.ImageUtils;
import com.anabada.neighbor.config.auth.PrincipalDetails;
import com.anabada.neighbor.file.controller.ImageController;
import com.anabada.neighbor.file.domain.ImageInfo;
import com.anabada.neighbor.file.service.FilesStorageService;
import com.anabada.neighbor.used.domain.Post;
import com.anabada.neighbor.used.service.UsedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ClubController {

    private final ClubService clubService;
    private final ImageUtils imageUtils;
    private final FilesStorageService storageService;
    private final ChattingService chattingService;
    private final UsedService usedService;

    public ClubController(ClubService clubService, ImageUtils imageUtils, FilesStorageService storageService, ChattingService chattingService, UsedService usedService) {
        this.clubService = clubService;
        this.imageUtils = imageUtils;
        this.storageService = storageService;
        this.chattingService = chattingService;
        this.usedService = usedService;
    }

    @GetMapping("/clubList")
    public String clubList(Model model, @RequestParam(value = "num", defaultValue = "0") int num, @RequestParam(value = "hobbyName", defaultValue = "전체모임") String hobbyName, @RequestParam(value = "search", defaultValue = "") String search) {
        Long hobbyId = clubService.findHobbyId(hobbyName);
        if (hobbyId == null) {
            hobbyId = 0L;
        }
        model.addAttribute("clubList", clubService.findClubList(num, hobbyId, search, "list", 0));
        model.addAttribute("hobby", clubService.findHobbyName());
        model.addAttribute("search", search);
        model.addAttribute("hobbyName", hobbyName);
        return num <= 0 ? "club/clubList" : "club/clubListPlus";
    }


//    //게시글 작성 페이지
//    @GetMapping("/clubSave")
//    public String clubSave(@RequestParam(value = "postId", required = false) Long postId
//            , HttpSession session, Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) {
//        if (postId != null) {//postId가 있으면 검색해서 정보 가져오기
//            ClubResponse clubResponse = clubService.findClub(postId, principalDetails);
//            model.addAttribute("club", clubResponse);
//        } else {
//            model.addAttribute("club", new ClubResponse());
//        }
//        return "club/clubSave";
//    }

    @PostMapping("/clubSave")
    public String clubSave(ClubRequest clubRequest, Model model, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        Post post = Post.builder()
                .memberId(principalDetails.getMember().getMemberId())
                .title(clubRequest.getTitle())
                .content(clubRequest.getContent())
                .postType("club")
                .build();
        long postId = clubService.savePost(post);
        if (postId == -1) {
            model.addAttribute("result", "글 등록실패!");
            return "club/clubSave";
        }
        Club club = Club.builder()
                .postId(postId)
                .memberId(post.getMemberId())
                .hobbyId(clubService.findHobbyId(clubRequest.getHobbyName()))
                .maxMan(clubRequest.getMaxMan())
                .build();
        if (clubService.saveClub(club) == 1) {
            chattingService.openRoom(postId, principalDetails, "club");
            List<ImageRequest> images = imageUtils.uploadImages(clubRequest.getImages());
            clubService.saveImages(postId, images);
            model.addAttribute("result", "글 등록성공!");//나중에 삭제
        } else {
            model.addAttribute("result", "글 등록실패!");//나중에 삭제
        }

        return "redirect:clubList";
    }

    @GetMapping("/clubDetail")
    public String clubDetail(@RequestParam(value = "postId", required = false) Long postId, Model model,
                             HttpServletRequest request, HttpServletResponse response,
                             @AuthenticationPrincipal PrincipalDetails principalDetails) {
        ClubResponse clubResponse = clubService.findClub(postId, principalDetails);
        List<ImageResponse> imageResponses = clubResponse.getImageResponseList();

        List<ImageInfo> imageInfose = new ArrayList<>();
        for (ImageResponse image : imageResponses) {
            ImageInfo imageInfo = ImageInfo.builder()
                    .name(image.getOrigName())
                    .url(MvcUriComponentsBuilder
                            .fromMethodName(ImageController.class, "getImage"
                            , image.getSaveName(), image.getCreaDate().format(DateTimeFormatter.ofPattern("yyMMdd"))).build().toString())
                    .build();
            imageInfose.add(imageInfo);
        }
        System.out.println(imageInfose);

        Cookie[] cookies = request.getCookies(); //쿠키 가져오기

        Cookie viewCookie = null;

        if (cookies != null && cookies.length > 0) { //가져온 쿠키가 있으면
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("cookie" + postId)) { //해당하는 게시물의 쿠키가 있으면
                    viewCookie = cookie; //viewCookie에 저장
                }
            }
        }
        if (viewCookie == null) { //쿠키가 없으면
            Cookie newCookie = new Cookie("cookie" + postId, String.valueOf(postId)); //해당하는 게시물의 새로운 쿠키 생성
            response.addCookie(newCookie); //쿠키 등록
            clubService.updatePostView(postId); //postId로 post 테이블에서 해당하는 튜플의 조회수 증가
        }

        model.addAttribute("images", imageInfose);
        model.addAttribute("club", clubResponse);
        model.addAttribute("postId", postId);
        model.addAttribute("hobby", clubService.findHobbyName());
        model.addAttribute("similarList", clubService.findClubList(0, clubService.findHobbyId(clubResponse.getHobbyName()), "", "similarList", postId));
        model.addAttribute("roomId", chattingService.findRoomId(postId));
        model.addAttribute("reportType", usedService.reportType());
        return "club/clubDetail";
    }

    @GetMapping("/clubRemove")
    public String clubRemove(Long postId) {
        clubService.deletePost(postId);
        return "redirect:clubList";
    }

    // 파일 리스트 조회
    @GetMapping("/posts/{postId}/images")
    @ResponseBody
    public List<ImageResponse> clubImage(@PathVariable Long postId) {
        return clubService.findAllImageByPostId(postId);
    }

    @PostMapping("/club/join")
    @ResponseBody
    public ClubResponse join(Long postId, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        long memberId = principalDetails.getMember().getMemberId();
        ClubResponse club = clubService.findClub(postId,principalDetails);
        if (club.getMemberId() == memberId) {//클럽글을 작성한 본인은 가입탈퇴 불가
            return null;
        }
        Long clubJoinId = clubService.findClubJoinByMemberId(club, memberId);

        if (clubJoinId == null) {//가입한적 없으면 join 있으면 delete
            if (clubService.joinClubJoin(club, principalDetails) == 1) { //인원이 꽉차 가입실패시 -1 반환
                clubService.updateNowMan(1, club.getClubId());

                chattingService.chatJoin(postId, principalDetails);

                return clubService.findClub(postId,principalDetails); // 가입성공시 클럽을 새로 조회
            }else{
                club.setClubJoinYn(-1);
                return club; // 가입 실패시 클럽을 새로조회하지않음
            }
        }else{
            if (clubService.deleteClubJoin(club, principalDetails) == 1) {
                clubService.updateNowMan(0, club.getClubId());

                chattingService.chatOut(Chat.builder()
                        .roomId(chattingService.findRoomId(postId))
                        .type("club")
                        .build(), principalDetails);

                return clubService.findClub(postId, principalDetails);// 탈퇴성공시 클럽을 새로 조회
            }else{
                return club; // 탈퇴 실패시 클럽을 새로 조회하지않음
            }
        }
    }

}
