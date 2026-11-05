# 晶片与论文研究（角色 5/10）

核对日期：2026-09-25。用途：工程研究边界，不是器件 BOM、测量校准、临床验证或诊断意见。

## 证据口径

- Xiaomi 官方 Watch 2 Pro 规格仅列光学心率、加速度计、陀螺仪、环境光、电子罗盘、气压计和生物电阻抗；未给芯片料号、传感器寄存器配置、波长、模拟增益或装置级校准常数。[Xiaomi 官方规格](https://www.mi.com/au/product/xiaomi-watch-2-pro/specs/)
- 项目验证计划记录的是 Sensor HAL endpoint 和旧采集结果，并明确说明家族名称不是确切 BOM；旧导出不等于当前构建的新鲜实机数据。[验证计划](wear-sensor-validation-plan.md) [校准状态](CHIP_CALIBRATION_STATUS.md)
- Android `SensorEvent.values` 的内容与单位由公开 sensor type 定义；厂商私有 sensor type / 字符串 endpoint 不会因为名称像 `spo2`、`temperature` 或 `ECG` 而自动获得相同单位。[SensorEvent API](https://developer.android.com/reference/android/hardware/SensorEvent) [Sensor API](https://developer.android.com/reference/android/hardware/Sensor)
- Wear OS Health Services 是平台对原始传感器和相关算法的抽象层；它可报告 bpm 等平台数据类型，但不能据此反推具体芯片、光学原始值或 OEM 校准实现。[Health Services](https://developer.android.com/health-and-fitness/health-services)

## 按已知家族假设 / endpoint 分组

这里“已知”只表示官方产品规格列出了传感器类别，或本项目历史 Sensor HAL 曾暴露相应 endpoint；**不表示确认了手表内具体芯片**。`MAX86178`、`CT7117`、`BMI26x`、`AK0991x`、`ICP101xx`、`LTR311`、LifeQ 和 `mi_spo2` 均保留为项目映射/待证标签。

| 组 / endpoint | 可做的工程信号分析（限定输入语义已确认） | 需要 OEM 校准、参考设备或装置验证的生理输出 | 当前未知 / 禁止推断 |
|---|---|---|---|
| 光学心率 / PPG；项目映射 MAX86178，另有 Health Services/LifeQ endpoint | 原始样本和时间戳完整性、饱和/填充值、窗口覆盖率、接触与运动质量；若采样时间轴和波形可用，做峰候选/频谱候选并标为研究估计。以同步 IMU 评估运动伪影、质量分段和静息/运动差异。 | 以 PPG 推出脉率需通道、时间轴、接触质量及对 ECG/合格参考脉搏计的装置级一致性验证；HRV 还需逐搏间期准确性、异位/伪差处理、采样和协议验证。Health Services 的带单位 HR 应标注该平台来源，不能伪称为本地 PPG 算法输出。 | LED 波长/电流、PD 通道映射、ADC 码义、采样时钟、OEM 滤波和脉率校准未知。运动时伪峰可能与心率频带重叠，质量差时应拒绝输出。 |
| SpO₂；`spo2_raw`、`mi_spo2` | 只可保存原始 endpoint/样本数/时间戳、传输完整性和原始质量描述；不可对未知编码套百分比范围或单位。 | SpO₂ 百分比依赖具体光路/波长、LED/PD 配置、比值算法和 OEM 经验校准；需要厂商比例曲线与按适用标准、对照血气/合适参考设备的临床验证。 | 项目记录出现过极大原始数值；不能由 endpoint 名、字段位置或偶然落在 70–100 推断百分比。未知量纲固定标 `spo2_vendor_unit_unverified`。 |
| ECG；项目 endpoint 映射 MAX86178（官方产品规格未列 ECG） | 若 HAL 事件确为有序原始波形：封包/样本数/时间戳对齐、缺口和重复检测、饱和与基线/工频/肌电噪声可视化；离线 QRS 候选可作为研究处理，并保存未经变换原始值与处理参数。滤波会改变形态，必须保留 raw 并报告滤波器。 | mV、导联解释、心率/RR/HRV，尤其节律或疾病分类，需端到端导联/增益/ADC 标定、逐点时钟、参考 ECG 配对和相应人群/临床验证；疾病功能另需适用法规审查。 | Watch 2 Pro ECGP/ECGN 接线、导联、ADC-to-mV、增益、实际 ADC 时钟和临床验证未公开。ADI 芯片能力或评估板设置不能代替 Xiaomi 整机 profile。不得宣称房颤或诊断。 |
| BioZ / 呼吸；项目映射 MAX86178 | 原始序列、事件间隔/丢包、接触变化和相对振荡特征；在基线稳定、采样时基已确认后，可研究呼吸周期候选并与独立呼吸参考比较。 | 欧姆/相位、呼吸率、通气、液体状态、体脂/体水等需要激励电流/频率、相位和电极模型、复数阻抗校准及特定输出的参考方法和人群验证。 | 激励波形/幅度/频率、接线几何、相位/阻抗换算未知。不得从量级猜 Ω、呼吸或体成分。ADI MAX86178 数据表只证芯片本身支持可配置 BioZ，不证其在本手表存在或怎样配置。 |
| 加速度/陀螺仪；项目映射 BMI26x | Android 标准型别且 units 确实对应时，可做轴向/模长、重力分离、步频/窗口活动分类、姿态变化、运动伪影协变量；模型须以目标佩戴位置和真实标注数据评估。 | 临床活动量、能量消耗、跌倒检测或跌倒风险需要整机/佩戴方式特定验证、标注基准、灵敏度/误报评估；不能由阈值或单一冲击直接认定跌倒。 | BMI26x 精确型号、轴变换/偏置、步数 OEM 算法和跌倒标签未知。自由生活泛化不能由受控实验分数替代。 |
| 电子罗盘/磁场/旋转向量；项目映射 AK0991x 与 Android fusion | 传感器字段单位和校准状态明确时，可估相对方向、姿态、硬/软铁畸变质量、航向抖动；融合方向需携带精度/失效状态并以已知方向做工程测试。 | 对生命体征没有合理直接推导。导航航向也需磁偏角、整机磁校准及环境测试，不得把 Android fused output 当芯片原始磁读数。 | AKM 精确型号、融合供应商、整机校准矩阵和周边磁干扰未知。 |
| 气压；项目映射 ICP101xx | Android `TYPE_PRESSURE` 明确定义 hPa；可对时间序列做漂移/跳变检测、同会话相对气压变化。相对高度只能在明确参考基线/气象条件下作工程近似。 | 海拔/爬升精度要与已知标高和当地气压参考验证；没有适当验证，不导出临床或身体状态。 | ICP 精确料号、OTP/补偿参数、海平面气压基准和整机高度误差未知。Android 单位定义不等于传感器准确度证书。 |
| 环境光；项目映射 LTR311 | Android `TYPE_LIGHT` 定义 lux；可作环境光趋势、窗口统计和情境特征；以校准 lux meter 检查整机误差。 | 无可由 lux 直接得到的生理结论；任何睡眠/昼夜节律推断需要专用协议、多模态及独立验证。 | LTR 精确料号、视场/罩壳影响和整机光谱响应未知。 |
| 温度 endpoint；项目映射 CT7117 | 若 endpoint 语义仍未证实，只记录 raw、时间和稳定性；若 OEM 确认是接触表面温度且单位/校准公开，可作对应测点趋势并记录佩戴/环境/热平衡条件。 | 皮肤温度绝对值需接触位置和温度参考校准；核心体温需要独立、适用活动/环境/人群的模型，并与核心参考温度验证。 | 官方规格未列温度传感器或 CT7117；旧项目导出出现温度类 endpoint 不足以确认芯片/位置/摄氏度，也不证明体温。板温、皮肤温度和核心体温不是可互换量。 |
| 步数/活动/HR；Health Services 与厂商 endpoint | 优先采用 Health Services 声明的 capability、availability、来源、事件时间戳和 API 单位；工程层可做新鲜度、窗口覆盖、来源冲突检查。 | 卡路里、运动强度、体能、睡眠阶段等算法输出须平台/OEM定义和目标用户验证；平台给值也不代表医疗诊断。 | 不能从 Health Services metric 倒推传感器型号/采样频率；不同数据类型时间戳不同，不能用一条全局时间戳伪装同步。 |

## 信号处理建议与安全边界

1. **先验数据契约**：固定来源、endpoint/type、数组长度、单位、时基、事件时戳、注册状态、佩戴/接触状态；缺一项就只做结构/完整性分析。Android 标准型别按官方定义处理，vendor-private 类型保持 opaque。
2. **保留原始值**：去基线、带通、陷波、重采样、插值、去运动伪影都产生派生序列；记录算法/参数/延迟并与 raw 并列，不让滤波后波形冒充校准原波形。ECG 过滤可能改变 ST/QRS 等形态。
3. **质量先于估计**：用饱和率、缺包、抖动、接触、运动协变量、峰形/峰间一致性、窗口覆盖和拒绝原因构建 SQI；质量不足时返回不可用状态，不能强行给 bpm/百分比。
4. **验证分层**：代码单元测试 → 合成/公开数据测试（不可代替手表）→ 同步参考仪器的静息与运动受控配对 → 目标人群/环境/佩戴方式外部验证。误差报告用偏差和一致性界限等，而不只相关系数。
5. **工程输出与生理输出分开**：原始趋势、峰候选、活动分类和相对高度属于工程/研究特征；HR、HRV、SpO₂、体温、呼吸率、体成分等数值需要对应模态的计量/装置校准与验证。诊断/风险判断还需要独立临床证据，本文不作此类输出。

## 可核验的论文（标题和 DOI 对出版方/PubMed 记录）

- Shahid Ismail, Usman Akram, Imran Siddiqi. “Heart rate tracking in photoplethysmography signals affected by motion artifacts: a review.” *EURASIP Journal on Advances in Signal Processing* 2021, Article 5. DOI: [10.1186/s13634-020-00714-2](https://doi.org/10.1186/s13634-020-00714-2). 支持 PPG 与加速度协同的去运动伪影/追踪问题；不验证本表设备或其输出。[出版方全文](https://link.springer.com/article/10.1186/s13634-020-00714-2)
- Sang Hyun Park, Su Hyeon Seok, Seung Hee Kim, Hyun Jae Shin. “Photoplethysmogram Analysis and Applications: An Integrative Review.” *Frontiers in Physiology* 2022;12:808451. DOI: [10.3389/fphys.2022.808451](https://doi.org/10.3389/fphys.2022.808451). 综述 PPG 波形、噪声、峰检测与 SQI；作者指出无标准化预处理管线。[PubMed](https://pubmed.ncbi.nlm.nih.gov/35300400/) [期刊](https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2022.808451/full)
- R. Al-Halawani, P. H. Charlton, M. Qassem, P. A. Kyriacou. “A review of the effect of skin pigmentation on pulse oximeter accuracy.” *Physiological Measurement* 2023;44(5):05TR01. DOI: [10.1088/1361-6579/acd51a](https://doi.org/10.1088/1361-6579/acd51a). This review discusses skin-pigmentation-related accuracy limitations across pulse oximeters; it does not calibrate or validate the Watch 2 Pro. [Publisher record](https://doi.org/10.1088/1361-6579/acd51a) [Open-access manuscript](https://openaccess.city.ac.uk/id/eprint/30597/)
- “Improving the Accuracy and Equity of Pulse Oximeters: Collaborative Recommendations.” *JACC: Advances* 2022. DOI: [10.1016/j.jacadv.2022.100118](https://doi.org/10.1016/j.jacadv.2022.100118). 出版方页面说明常规 SpO₂ 依赖波长和经验校准，并建议设备验证；不提供 Watch 2 Pro 校准。[出版方](https://www.jacc.org/doi/10.1016/j.jacadv.2022.100118)
- Narayanan A, Desai F, Stewart T, Duncan S, Mackay L. “Application of Raw Accelerometer Data and Machine-Learning Techniques to Characterize Human Movement Behavior: A Systematic Scoping Review.” *Journal of Physical Activity and Health* 2020;17(3):360–383. DOI: [10.1123/jpah.2019-0088](https://doi.org/10.1123/jpah.2019-0088). 综述活动分类；指出自由生活环境的适用性尚不确定。[PubMed](https://pubmed.ncbi.nlm.nih.gov/32035416/)
- Conor M. Dolson, Ethan R. Harlow, Dermot M. Phelan, Tim J. Gabbett, G. Gaal, C. McMellen, James E. Voos, Dhruv R. Seshadri. “Wearable Sensor Technology to Predict Core Body Temperature: A Systematic Review.” *Sensors* 2022;22(19):7639. DOI: [10.3390/s22197639](https://doi.org/10.3390/s22197639). 汇总可穿戴核心温度模型和验证异质性；不支持由本设备温度原始字段直接当核心温度。[PubMed Central](https://pmc.ncbi.nlm.nih.gov/articles/PMC9572283/)
- “The evolution of bioimpedance analysis: From traditional methods to wearable technology.” *Nutrition* 2025;129:112601. DOI: [10.1016/j.nut.2024.112601](https://doi.org/10.1016/j.nut.2024.112601). 综述指出可穿戴 BIA 与 DXA/四成分模型仍有精度差异、需要进一步验证。[出版方](https://www.sciencedirect.com/science/article/pii/S0899900724002508)
- Malik M, et al. “Heart rate variability: standards of measurement, physiological interpretation and clinical use.” *Circulation* 1996;93(5):1043–1065. DOI: [10.1161/01.CIR.93.5.1043](https://doi.org/10.1161/01.CIR.93.5.1043). HRV 测量规范基础文献；并非 PPG wrist device 的验证。[PubMed](https://pubmed.ncbi.nlm.nih.gov/8598068/)
- Shen Luo, Paul Johnston. “A review of electrocardiogram filtering.” *Journal of Electrocardiology* 2010;43(6):486–496. DOI: [10.1016/j.jelectrocard.2010.07.007](https://doi.org/10.1016/j.jelectrocard.2010.07.007). 讨论 ECG 预处理与滤波失真风险。[PubMed](https://pubmed.ncbi.nlm.nih.gov/20851409/)

其余 DOI 已由 DOI 链接及出版方/PubMed 页面核对；未能完整核验的候选论文不列入引用。

## 原始资料

- Analog Devices, **MAX86178 Data Sheet, Rev. 4**（官方产品页提供数据表）。该 AFE 的 ECG/BioZ/光学能力及可编程性是芯片级规格，不证明 Watch 2 Pro 使用该芯片或采用特定配置。[ADI 产品与数据表入口](https://www.analog.com/en/products/max86178.html) [官方 PDF](https://www.analog.com/media/en/technical-documentation/data-sheets/max86178.pdf)
- Android Developers, [SensorEvent API](https://developer.android.com/reference/android/hardware/SensorEvent), [Sensor API](https://developer.android.com/reference/android/hardware/Sensor), [Wear OS Health Services](https://developer.android.com/health-and-fitness/health-services)。用于确认平台字段语义、时间戳和 API 抽象边界。
- Xiaomi, [Watch 2 Pro 官方规格](https://www.mi.com/au/product/xiaomi-watch-2-pro/specs/)。传感器类别清单；页面没有给出本备忘录所列芯片型号或校准参数。

## 本项目证据状态

验证计划最后记录的硬件导出早于当时双端当前安装版本；近期 watch ADB 连接不可用，故当前不能据此宣称新版本 endpoint 已重测。历史数据仅证明曾经暴露/收到某些 HAL 原始事件。按 acquisition → payload/units → quality/calibration → snapshot → Data Layer → phone storage → freshness → UI 顺序，任何当前生理输出都必须等待新鲜配对设备证据；本研究备忘录本身不改变应用输出或校准状态。
