package akoletter.devakoletterapi.util.File.service;

import akoletter.devakoletterapi.jpa.filemst.entity.FileMst;
import akoletter.devakoletterapi.jpa.filemst.repo.FileMstRepository;
import akoletter.devakoletterapi.util.File.controller.FileHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@Service
public class FileServiceImpl implements FileService {

    private final FileMstRepository fileMstRepository;
    private final FileHandler fileHandler;
    
    @Autowired
    public FileServiceImpl(FileMstRepository fileMstRepository) {
        this.fileMstRepository = fileMstRepository;
        this.fileHandler = new FileHandler(fileMstRepository); //생성자
    }
    //저장해주는 메소드
    public List<FileMst> saveFile(
            FileMst board,
            List<MultipartFile> files
    ) throws Exception {
        // 파일을 저장하고 그 Board 에 대한 list 를 가지고 있는다

        FileMst last = fileMstRepository.findTopByOrderByFileIdDesc().orElse(null);
        int fileId = 1;
        if(last != null){
            fileId = last.getFileId()+1;
        }
        List<FileMst> list = fileHandler.parseFileInfo(fileId, files);
        return list;
    }
}
