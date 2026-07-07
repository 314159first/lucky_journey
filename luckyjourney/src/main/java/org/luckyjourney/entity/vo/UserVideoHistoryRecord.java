package org.luckyjourney.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVideoHistoryRecord {

    private Long videoId;

    private Date viewedAt;
}
