package com.sitewhere.rdb.entities;



import com.sitewhere.spi.device.DeviceAssignmentStatus;
import com.sitewhere.spi.device.IDeviceAssignment;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "device_assignment")
public class DeviceAssignment implements IDeviceAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Device id */
    private UUID deviceId;

    /** Device type id */
    private UUID deviceTypeId;

    /** Id of assigned customer */
    private UUID customerId;

    /** Id of assigned area */
    private UUID areaId;

    /** Id of assigned asset */
    private UUID assetId;

    /** Assignment status */
    private DeviceAssignmentStatus status;

    /** Assignment start date */
    private Date activeDate;

    /** Assignment end date */
    private Date releasedDate;

    private String token;

    private Date createdDate;

    private String createdBy;

    private Date updatedDate;

    private String updatedBy;

    @ElementCollection
    @CollectionTable(name="device_assignment_metadata")
    @MapKeyColumn(name="propKey")
    @Column(name="propValue")
    private Map<String, String> metadata = new HashMap<>();

    @Override
    public UUID getDeviceId() {
        return deviceId;
    }

    @Override
    public UUID getDeviceTypeId() {
        return deviceTypeId;
    }

    @Override
    public UUID getCustomerId() {
        return customerId;
    }

    @Override
    public UUID getAreaId() {
        return areaId;
    }

    @Override
    public UUID getAssetId() {
        return assetId;
    }

    @Override
    public DeviceAssignmentStatus getStatus() {
        return status;
    }

    @Override
    public Date getActiveDate() {
        return activeDate;
    }

    @Override
    public Date getReleasedDate() {
        return releasedDate;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public Date getCreatedDate() {
        return createdDate;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public Date getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public String getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public void setDeviceTypeId(UUID deviceTypeId) {
        this.deviceTypeId = deviceTypeId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public void setAreaId(UUID areaId) {
        this.areaId = areaId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public void setStatus(DeviceAssignmentStatus status) {
        this.status = status;
    }

    public void setActiveDate(Date activeDate) {
        this.activeDate = activeDate;
    }

    public void setReleasedDate(Date releasedDate) {
        this.releasedDate = releasedDate;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedDate(Date updatedDate) {
        this.updatedDate = updatedDate;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}