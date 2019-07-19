package com.sitewhere.device.persistence.rdb;

import com.google.common.collect.Lists;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.DeleteResult;
import com.sitewhere.common.MarshalUtils;
import com.sitewhere.device.DeviceManagementUtils;
import com.sitewhere.device.microservice.DeviceManagementMicroservice;
import com.sitewhere.device.persistence.DeviceManagementPersistence;
import com.sitewhere.device.persistence.TreeBuilder;
import com.sitewhere.device.persistence.mongodb.MongoArea;
import com.sitewhere.device.persistence.mongodb.MongoDeviceGroup;
import com.sitewhere.device.persistence.mongodb.MongoDeviceGroupElement;
import com.sitewhere.mongodb.MongoPersistence;
import com.sitewhere.mongodb.common.MongoPersistentEntity;
import com.sitewhere.rdb.DbClient;
import com.sitewhere.rest.model.area.Area;
import com.sitewhere.rest.model.area.AreaType;
import com.sitewhere.rest.model.area.Zone;
import com.sitewhere.rest.model.customer.Customer;
import com.sitewhere.rest.model.customer.CustomerType;
import com.sitewhere.rest.model.device.*;
import com.sitewhere.rest.model.device.command.DeviceCommand;
import com.sitewhere.rest.model.device.group.DeviceGroup;
import com.sitewhere.rest.model.device.group.DeviceGroupElement;
import com.sitewhere.rest.model.search.area.AreaSearchCriteria;
import com.sitewhere.rest.model.search.customer.CustomerSearchCriteria;
import com.sitewhere.rest.model.search.device.DeviceCommandSearchCriteria;
import com.sitewhere.rest.model.search.device.DeviceStatusSearchCriteria;
import com.sitewhere.server.lifecycle.TenantEngineLifecycleComponent;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.SiteWhereSystemException;
import com.sitewhere.spi.area.IArea;
import com.sitewhere.spi.area.IAreaType;
import com.sitewhere.spi.area.IZone;
import com.sitewhere.spi.area.request.IAreaCreateRequest;
import com.sitewhere.spi.area.request.IAreaTypeCreateRequest;
import com.sitewhere.spi.area.request.IZoneCreateRequest;
import com.sitewhere.spi.asset.IAsset;
import com.sitewhere.spi.asset.IAssetManagement;
import com.sitewhere.spi.customer.ICustomer;
import com.sitewhere.spi.customer.ICustomerType;
import com.sitewhere.spi.customer.request.ICustomerCreateRequest;
import com.sitewhere.spi.customer.request.ICustomerTypeCreateRequest;
import com.sitewhere.spi.device.*;
import com.sitewhere.spi.device.command.IDeviceCommand;
import com.sitewhere.spi.device.group.IDeviceGroup;
import com.sitewhere.spi.device.group.IDeviceGroupElement;
import com.sitewhere.spi.device.request.*;
import com.sitewhere.spi.error.ErrorCode;
import com.sitewhere.spi.error.ErrorLevel;
import com.sitewhere.spi.search.ISearchCriteria;
import com.sitewhere.spi.search.ISearchResults;
import com.sitewhere.spi.search.ITreeNode;
import com.sitewhere.spi.search.area.IAreaSearchCriteria;
import com.sitewhere.spi.search.customer.ICustomerSearchCriteria;
import com.sitewhere.spi.search.device.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.util.*;

public class RDBDeviceManagement extends TenantEngineLifecycleComponent implements IDeviceManagement {

    /** Injected with global SiteWhere relational database client */
    private DbClient dbClient;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public IDeviceType createDeviceType(IDeviceTypeCreateRequest request) throws SiteWhereException {
        DeviceType deviceType = DeviceManagementPersistence.deviceTypeCreateLogic(request);
        com.sitewhere.rdb.entities.DeviceType created = new com.sitewhere.rdb.entities.DeviceType();
        BeanUtils.copyProperties(deviceType, created);
        created = dbClient.getDbManager().getDeviceTypeRepository().save(created);
        return created;
    }

    @Override
    public IDeviceType getDeviceType(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceType> opt = dbClient.getDbManager().getDeviceTypeRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceType getDeviceTypeByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceType> opt = dbClient.getDbManager().getDeviceTypeRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceType updateDeviceType(UUID id, IDeviceTypeCreateRequest request) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceType> opt = dbClient.getDbManager().getDeviceTypeRepository().findById(id);
        com.sitewhere.rdb.entities.DeviceType updated = null;
        if(opt.isPresent()) {
            updated = opt.get();
            DeviceType deviceType = new DeviceType();
            deviceType.setId(id);
            DeviceManagementPersistence.deviceTypeUpdateLogic(request, deviceType);
            BeanUtils.copyProperties(deviceType, updated);
            updated = dbClient.getDbManager().getDeviceTypeRepository().save(updated);
        }
        return updated;
    }

    @Override
    public ISearchResults<IDeviceType> listDeviceTypes(ISearchCriteria criteria) throws SiteWhereException {
        Iterable<com.sitewhere.rdb.entities.DeviceType> iter = dbClient.getDbManager().getDeviceTypeRepository().findAllOrderByCreatedDateDesc();
        List<com.sitewhere.rdb.entities.DeviceType> list = Lists.newArrayList(iter);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IDeviceType deleteDeviceType(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceType> opt = dbClient.getDbManager().getDeviceTypeRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getDeviceTypeRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public IDeviceCommand createDeviceCommand(IDeviceCommandCreateRequest request) throws SiteWhereException {
        // Validate device type token passed.
        if (request.getDeviceTypeToken() == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
        }
        Optional<com.sitewhere.rdb.entities.DeviceType> opt = dbClient.getDbManager().getDeviceTypeRepository().findByToken(request.getDeviceTypeToken());
        if(!opt.isPresent()) {
            throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
        }
        com.sitewhere.rdb.entities.DeviceType created = opt.get();

        DeviceCommandSearchCriteria criteria = new DeviceCommandSearchCriteria(1, 0);
        criteria.setDeviceTypeId(created.getId());
        ISearchResults<IDeviceCommand> existing = listDeviceCommands(criteria);

        DeviceType deviceType = new DeviceType();
        deviceType.setId(created.getId());

        // Use common logic so all backend implementations work the same.
        DeviceCommand command = DeviceManagementPersistence.deviceCommandCreateLogic(deviceType, request,
                existing.getResults());

        com.sitewhere.rdb.entities.DeviceCommand newCommand = new com.sitewhere.rdb.entities.DeviceCommand();
        BeanUtils.copyProperties(command, newCommand);

        newCommand = dbClient.getDbManager().getDeviceCommandRepository().save(newCommand);
        return newCommand;
    }

    @Override
    public IDeviceCommand getDeviceCommand(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceCommand> opt = dbClient.getDbManager().getDeviceCommandRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceCommand getDeviceCommandByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceCommand> opt = dbClient.getDbManager().getDeviceCommandRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceCommand updateDeviceCommand(UUID id, IDeviceCommandCreateRequest request) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceCommand> opt = dbClient.getDbManager().getDeviceCommandRepository().findById(id);
        com.sitewhere.rdb.entities.DeviceCommand updated = new com.sitewhere.rdb.entities.DeviceCommand();
        if(opt.isPresent()) {
            // Validate device type token passed.
            IDeviceType deviceType = null;
            if (request.getDeviceTypeToken() != null) {
                deviceType = getDeviceTypeByToken(request.getDeviceTypeToken());
                if (deviceType == null) {
                    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
                }
            } else {
                deviceType = getDeviceType(opt.get().getDeviceTypeId());
            }

            DeviceCommandSearchCriteria criteria = new DeviceCommandSearchCriteria(1, 0);
            criteria.setDeviceTypeId(deviceType.getId());
            ISearchResults<IDeviceCommand> existing = listDeviceCommands(criteria);

            DeviceCommand target = new DeviceCommand();
            // Use common update logic.
            DeviceManagementPersistence.deviceCommandUpdateLogic(deviceType, request, target, existing.getResults());
            BeanUtils.copyProperties(target, updated);

            updated = dbClient.getDbManager().getDeviceCommandRepository().save(updated);
        }
        return updated;
    }

    @Override
    public ISearchResults<IDeviceCommand> listDeviceCommands(IDeviceCommandSearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.DeviceCommand> specification = new Specification<com.sitewhere.rdb.entities.DeviceCommand>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.DeviceCommand> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (criteria.getDeviceTypeId() != null) {
                    Path path = root.get("deviceTypeId");
                    predicates.add(cb.equal(path, criteria.getDeviceTypeId()));
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };

        List<com.sitewhere.rdb.entities.DeviceCommand> list = dbClient.getDbManager().getDeviceCommandRepository().findAllOrderByName(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IDeviceCommand deleteDeviceCommand(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceCommand> opt = dbClient.getDbManager().getDeviceCommandRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getDeviceCommandRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public IDeviceStatus createDeviceStatus(IDeviceStatusCreateRequest request) throws SiteWhereException {
        if (request.getDeviceTypeToken() == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
        }
        IDeviceType deviceType = getDeviceTypeByToken(request.getDeviceTypeToken());
        if (deviceType == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
        }
        DeviceStatusSearchCriteria criteria = new DeviceStatusSearchCriteria(1, 0);
        criteria.setDeviceTypeId(deviceType.getId());
        ISearchResults<IDeviceStatus> existing = listDeviceStatuses(criteria);

        // Use common logic so all backend implementations work the same.
        DeviceStatus status = DeviceManagementPersistence.deviceStatusCreateLogic(deviceType, request,
                existing.getResults());

        com.sitewhere.rdb.entities.DeviceStatus created = new com.sitewhere.rdb.entities.DeviceStatus();
        BeanUtils.copyProperties(status, created);
        created = dbClient.getDbManager().getDeviceStatusRepository().save(created);
        return created;
    }

    @Override
    public IDeviceStatus getDeviceStatus(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceStatus> opt = dbClient.getDbManager().getDeviceStatusRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceStatus getDeviceStatusByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceStatus> opt = dbClient.getDbManager().getDeviceStatusRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceStatus updateDeviceStatus(UUID id, IDeviceStatusCreateRequest request) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceStatus> opt = dbClient.getDbManager().getDeviceStatusRepository().findById(id);
        if(opt.isPresent()) {
            com.sitewhere.rdb.entities.DeviceStatus updated = opt.get();

            // Validate device type token passed.
            IDeviceType deviceType = null;
            if (request.getDeviceTypeToken() != null) {
                deviceType = getDeviceTypeByToken(request.getDeviceTypeToken());
                if (deviceType == null) {
                    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
                }
            } else {
                deviceType = getDeviceType(updated.getDeviceTypeId());
            }
            DeviceStatusSearchCriteria criteria = new DeviceStatusSearchCriteria(1, 0);
            criteria.setDeviceTypeId(deviceType.getId());
            ISearchResults<IDeviceStatus> existing = listDeviceStatuses(criteria);
            DeviceStatus target = new DeviceStatus();
            // Use common update logic.
            DeviceManagementPersistence.deviceStatusUpdateLogic(deviceType, request, target, existing.getResults());
            BeanUtils.copyProperties(target, updated);
            updated = dbClient.getDbManager().getDeviceStatusRepository().save(updated);
            return updated;
        }
        return null;
    }

    @Override
    public ISearchResults<IDeviceStatus> listDeviceStatuses(IDeviceStatusSearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.DeviceStatus> specification = new Specification<com.sitewhere.rdb.entities.DeviceStatus>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.DeviceStatus> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (criteria.getDeviceTypeId() != null) {
                    Path path = root.get("deviceTypeId");
                    predicates.add(cb.equal(path, criteria.getDeviceTypeId()));
                }
                if (criteria.getCode() != null) {
                    Path path = root.get("code");
                    predicates.add(cb.equal(path, criteria.getCode()));
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };

        List<com.sitewhere.rdb.entities.DeviceStatus> list = dbClient.getDbManager().getDeviceStatusRepository().findAllOrderByName(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IDeviceStatus deleteDeviceStatus(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceStatus> opt = dbClient.getDbManager().getDeviceStatusRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getDeviceStatusRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public IDevice createDevice(IDeviceCreateRequest request) throws SiteWhereException {
        IDeviceType deviceType = getDeviceTypeByToken(request.getDeviceTypeToken());
        if (deviceType == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
        }
        Device newDevice = DeviceManagementPersistence.deviceCreateLogic(request, deviceType);
        com.sitewhere.rdb.entities.Device created = new com.sitewhere.rdb.entities.Device();
        BeanUtils.copyProperties(newDevice, created);
        created = dbClient.getDbManager().getDeviceRepository().save(created);
        return created;
    }

    @Override
    public IDevice getDevice(UUID deviceId) throws SiteWhereException {
        Optional< com.sitewhere.rdb.entities.Device> opt = dbClient.getDbManager().getDeviceRepository().findById(deviceId);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDevice getDeviceByToken(String token) throws SiteWhereException {
        Optional< com.sitewhere.rdb.entities.Device> opt = dbClient.getDbManager().getDeviceRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDevice updateDevice(UUID deviceId, IDeviceCreateRequest request) throws SiteWhereException {
        getLogger().info("Request:\n\n" + MarshalUtils.marshalJsonAsPrettyString(request));
        Optional<com.sitewhere.rdb.entities.Device> opt = dbClient.getDbManager().getDeviceRepository().findById(deviceId);
        com.sitewhere.rdb.entities.Device updated = new com.sitewhere.rdb.entities.Device();
        if(opt.isPresent()) {
            updated = opt.get();

            IDeviceType deviceType = null;
            if (request.getDeviceTypeToken() != null) {
                deviceType = getDeviceTypeByToken(request.getDeviceTypeToken());
                if (deviceType == null) {
                    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceTypeToken, ErrorLevel.ERROR);
                }
            }

            IDevice parent = null;
            if (request.getParentDeviceToken() != null) {
                parent = getDeviceByToken(request.getParentDeviceToken());
                if (parent == null) {
                    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceToken, ErrorLevel.ERROR);
                }
            }

            Device target = new Device();
            DeviceManagementPersistence.deviceUpdateLogic(request, deviceType, parent, target);
            getLogger().info("Updated:\n\n" + MarshalUtils.marshalJsonAsPrettyString(target));

            BeanUtils.copyProperties(target, updated);
            updated = dbClient.getDbManager().getDeviceRepository().save(updated);
            return updated;
        }
        return null;
    }

    @Override
    public ISearchResults<IDevice> listDevices(IDeviceSearchCriteria criteria) throws SiteWhereException {
        IDeviceType deviceType = getDeviceTypeByToken(criteria.getDeviceTypeToken());
        Specification<com.sitewhere.rdb.entities.Device> specification = new Specification<com.sitewhere.rdb.entities.Device>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.Device> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (criteria.isExcludeAssigned()) {
                    Path path = root.get("activeDeviceAssignmentIds");
                    predicates.add(cb.not(cb.size(path).isNull()));
                    predicates.add(cb.not(cb.size(path).in(0)));
                }
                if (criteria.getStartDate() != null) {
                    Path path = root.get("createdDate");
                    predicates.add(cb.greaterThanOrEqualTo(path, criteria.getStartDate()));
                }
                if (criteria.getEndDate() != null) {
                    Path path = root.get("createdDate");
                    predicates.add(cb.lessThanOrEqualTo(path, criteria.getEndDate()));
                }
                if (!StringUtils.isEmpty(criteria.getDeviceTypeToken())) {
                    Path path = root.get("deviceTypeId");
                    predicates.add(cb.equal(path, deviceType.getId()));
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };
        List<com.sitewhere.rdb.entities.Device> list = dbClient.getDbManager().getDeviceRepository().findAllOrderByCreatedDateDesc(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IDevice createDeviceElementMapping(UUID deviceId, IDeviceElementMapping mapping) throws SiteWhereException {
        IDevice device = getApiDeviceById(deviceId);
        return DeviceManagementPersistence.deviceElementMappingCreateLogic(this, device, mapping);
    }

    @Override
    public IDevice deleteDeviceElementMapping(UUID deviceId, String path) throws SiteWhereException {
        IDevice device = getApiDeviceById(deviceId);
        return DeviceManagementPersistence.deviceElementMappingDeleteLogic(this, device, path);
    }

    @Override
    public IDevice deleteDevice(UUID deviceId) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Device> opt = dbClient.getDbManager().getDeviceRepository().findById(deviceId);
        if(opt.isPresent()) {
            dbClient.getDbManager().getDeviceRepository().deleteById(deviceId);
        }
        return opt.get();
    }

    @Override
    public IDeviceAssignment createDeviceAssignment(IDeviceAssignmentCreateRequest request) throws SiteWhereException {
        IDevice existing = getDeviceByToken(request.getDeviceToken());
        // Look up customer if specified.
        ICustomer customer = null;
        if (request.getCustomerToken() != null) {
            customer = getCustomerByToken(request.getCustomerToken());
            if (customer == null) {
                throw new SiteWhereSystemException(ErrorCode.InvalidCustomerToken, ErrorLevel.ERROR);
            }
        }

        // Look up area if specified.
        IArea area = null;
        if (request.getAreaToken() != null) {
            area = getAreaByToken(request.getAreaToken());
            if (area == null) {
                throw new SiteWhereSystemException(ErrorCode.InvalidAreaToken, ErrorLevel.ERROR);
            }
        }

        // Look up asset if specified.
        IAsset asset = null;
        if (request.getAssetToken() != null) {
            asset = getAssetManagement().getAssetByToken(request.getAssetToken());
            if (asset == null) {
                getLogger().warn("Assignment references invalid asset token: " + request.getAssetToken());
                throw new SiteWhereSystemException(ErrorCode.InvalidAssetToken, ErrorLevel.ERROR);
            }
        }

        // Use common logic to load assignment from request.
        DeviceAssignment newAssignment = DeviceManagementPersistence.deviceAssignmentCreateLogic(request, customer,
                area, asset, existing);

        com.sitewhere.rdb.entities.DeviceAssignment created = new com.sitewhere.rdb.entities.DeviceAssignment();
        BeanUtils.copyProperties(newAssignment, created);
        created = dbClient.getDbManager().getDeviceAssignmentRepository().save(created);
        return created;
    }

    @Override
    public IDeviceAssignment getDeviceAssignment(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceAssignment> opt = dbClient.getDbManager().getDeviceAssignmentRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceAssignment getDeviceAssignmentByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceAssignment> opt = dbClient.getDbManager().getDeviceAssignmentRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public List<IDeviceAssignment> getActiveDeviceAssignments(UUID deviceId) throws SiteWhereException {
        List<com.sitewhere.rdb.entities.DeviceAssignment> list = dbClient.getDbManager().getDeviceAssignmentRepository().findByDeviceId(deviceId);
        List<IDeviceAssignment> newList = new ArrayList<>();
        for(com.sitewhere.rdb.entities.DeviceAssignment deviceAssignment : list) {
            newList.add(deviceAssignment);
        }
        return newList;
    }

    @Override
    public IDeviceAssignment updateDeviceAssignment(UUID id, IDeviceAssignmentCreateRequest request) throws SiteWhereException {
        return null;
    }

    @Override
    public ISearchResults<IDeviceAssignment> listDeviceAssignments(IDeviceAssignmentSearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.DeviceAssignment> specification = new Specification<com.sitewhere.rdb.entities.DeviceAssignment>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.DeviceAssignment> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();

                if ((criteria.getAssignmentStatuses() != null) && (criteria.getAssignmentStatuses().size() > 0)) {
                    Path path = root.get("status");
                    List<String> names = DeviceManagementUtils.getAssignmentStatusNames(criteria.getAssignmentStatuses());
                    predicates.add(cb.in(path));
                }
                if ((criteria.getDeviceTokens() != null) && (criteria.getDeviceTokens().size() > 0)) {
                    try {
                        List<UUID> ids = DeviceManagementUtils.getDeviceIds(criteria.getDeviceTokens(), RDBDeviceManagement.this);
                        Path path = root.get("deviceId");
                        predicates.add(cb.in(path));
                    } catch (SiteWhereException e) {
                        e.printStackTrace();
                    }
                }
                if ((criteria.getCustomerTokens() != null) && (criteria.getCustomerTokens().size() > 0)) {
                    try {
                        List<UUID> ids = DeviceManagementUtils.getCustomerIds(criteria.getCustomerTokens(), RDBDeviceManagement.this);
                        Path path = root.get("customerId");
                        predicates.add(cb.in(path));
                    } catch (SiteWhereException e) {
                        e.printStackTrace();
                    }
                }
                if ((criteria.getAreaTokens() != null) && (criteria.getAreaTokens().size() > 0)) {
                    try {
                        List<UUID> ids = DeviceManagementUtils.getAreaIds(criteria.getAreaTokens(), RDBDeviceManagement.this);
                        Path path = root.get("areaId");
                        predicates.add(cb.in(path));
                    } catch (SiteWhereException e) {
                        e.printStackTrace();
                    }
                }
                if ((criteria.getAssetTokens() != null) && (criteria.getAssetTokens().size() > 0)) {
                    try {
                        List<UUID> ids = getAssetIds(criteria.getAssetTokens());
                        Path path = root.get("assetId");
                        predicates.add(cb.in(path));
                    } catch (SiteWhereException e) {
                        e.printStackTrace();
                    }
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };
        List<com.sitewhere.rdb.entities.DeviceAssignment> list = dbClient.getDbManager().getDeviceAssignmentRepository().findAllOrderByActiveDateDesc(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IDeviceAssignment endDeviceAssignment(UUID id) throws SiteWhereException {
        return null;
    }

    @Override
    public IDeviceAssignment deleteDeviceAssignment(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceAssignment> opt = dbClient.getDbManager().getDeviceAssignmentRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getDeviceAssignmentRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public IDeviceAlarm createDeviceAlarm(IDeviceAlarmCreateRequest request) throws SiteWhereException {
        IDeviceAssignment assignment = getDeviceAssignmentByToken(request.getDeviceAssignmentToken());
        if (assignment == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidDeviceAssignmentToken, ErrorLevel.ERROR);
        }

        // Use common logic to load alarm from request.
        DeviceAlarm newAlarm = DeviceManagementPersistence.deviceAlarmCreateLogic(assignment, request);
        com.sitewhere.rdb.entities.DeviceAlarm created = new com.sitewhere.rdb.entities.DeviceAlarm();
        BeanUtils.copyProperties(newAlarm, created);
        created = dbClient.getDbManager().getDeviceAlarmRepository().save(created);
        return created;
    }

    @Override
    public IDeviceAlarm updateDeviceAlarm(UUID id, IDeviceAlarmCreateRequest request) throws SiteWhereException {
        return null;
    }

    @Override
    public IDeviceAlarm getDeviceAlarm(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceAlarm> opt = dbClient.getDbManager().getDeviceAlarmRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public ISearchResults<IDeviceAlarm> searchDeviceAlarms(IDeviceAlarmSearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.DeviceAlarm> specification = new Specification<com.sitewhere.rdb.entities.DeviceAlarm>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.DeviceAlarm> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (criteria.getDeviceId() != null) {
                    Path path = root.get("deviceId");
                    predicates.add(cb.equal(path, criteria.getDeviceId()));
                }
                if (criteria.getDeviceAssignmentId() != null) {
                    Path path = root.get("deviceAssignmentId");
                    predicates.add(cb.equal(path, criteria.getDeviceAssignmentId()));
                }
                if (criteria.getCustomerId() != null) {
                    Path path = root.get("customerId");
                    predicates.add(cb.equal(path, criteria.getCustomerId()));
                }
                if (criteria.getAreaId() != null) {
                    Path path = root.get("areaId");
                    predicates.add(cb.equal(path, criteria.getAreaId()));
                }
                if (criteria.getAssetId() != null) {
                    Path path = root.get("assetId");
                    predicates.add(cb.equal(path, criteria.getAssetId()));
                }
                if (criteria.getState() != null) {
                    Path path = root.get("state");
                    predicates.add(cb.equal(path, criteria.getState()));
                }
                if (criteria.getTriggeringEventId() != null) {
                    Path path = root.get("triggeringEventId");
                    predicates.add(cb.equal(path, criteria.getTriggeringEventId()));
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };
        List<com.sitewhere.rdb.entities.DeviceAlarm> list = dbClient.getDbManager().getDeviceAlarmRepository().findAllOrderByTriggeredDateDesc(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IDeviceAlarm deleteDeviceAlarm(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceAlarm> opt = dbClient.getDbManager().getDeviceAlarmRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getDeviceAlarmRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public ICustomerType createCustomerType(ICustomerTypeCreateRequest request) throws SiteWhereException {
        // Convert contained customer type tokens to ids.
        List<UUID> cctids = convertCustomerTypeTokensToIds(request.getContainedCustomerTypeTokens());

        // Use common logic so all backend implementations work the same.
        CustomerType type = DeviceManagementPersistence.customerTypeCreateLogic(request, cctids);
        com.sitewhere.rdb.entities.CustomerType created = new com.sitewhere.rdb.entities.CustomerType();

        BeanUtils.copyProperties(type, created);
        created = dbClient.getDbManager().getCustomerTypeRepository().save(created);
        return created;
    }

    @Override
    public ICustomerType getCustomerType(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.CustomerType> opt = dbClient.getDbManager().getCustomerTypeRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public ICustomerType getCustomerTypeByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.CustomerType> opt = dbClient.getDbManager().getCustomerTypeRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public ICustomerType updateCustomerType(UUID id, ICustomerTypeCreateRequest request) throws SiteWhereException {
        return null;
    }

    @Override
    public ISearchResults<ICustomerType> listCustomerTypes(ISearchCriteria criteria) throws SiteWhereException {
        List<com.sitewhere.rdb.entities.CustomerType> list = dbClient.getDbManager().getCustomerTypeRepository().findAllOrderByName();
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public ICustomerType deleteCustomerType(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.CustomerType> opt = dbClient.getDbManager().getCustomerTypeRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getCustomerTypeRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public ICustomer createCustomer(ICustomerCreateRequest request) throws SiteWhereException {
        // Look up customer type.
        ICustomerType customerType = getCustomerTypeByToken(request.getCustomerTypeToken());
        if (customerType == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidCustomerTypeToken, ErrorLevel.ERROR);
        }

        // Look up parent customer.
        ICustomer parentCustomer = (request.getParentToken() != null) ? getCustomerByToken(request.getParentToken())
                : null;

        // Use common logic so all backend implementations work the same.
        Customer customer = DeviceManagementPersistence.customerCreateLogic(request, customerType, parentCustomer);
        com.sitewhere.rdb.entities.Customer created = new com.sitewhere.rdb.entities.Customer();
        BeanUtils.copyProperties(customer, created);
        created = dbClient.getDbManager().getCustomerRepository().save(created);
        return created;
    }

    @Override
    public ICustomer getCustomer(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Customer> opt = dbClient.getDbManager().getCustomerRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public ICustomer getCustomerByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Customer> opt = dbClient.getDbManager().getCustomerRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public List<ICustomer> getCustomerChildren(String token) throws SiteWhereException {
        ICustomer existing = getCustomerByToken(token);
        if (existing == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidCustomerToken, ErrorLevel.ERROR);
        }
        List<com.sitewhere.rdb.entities.Customer> list = dbClient.getDbManager().getCustomerRepository().findListByParentId(existing.getId());
        List<ICustomer> newList = new ArrayList<>();
        for(com.sitewhere.rdb.entities.Customer customer : list) {
            newList.add(customer);
        }
        return newList;
    }

    @Override
    public ICustomer updateCustomer(UUID id, ICustomerCreateRequest request) throws SiteWhereException {
        return null;
    }

    @Override
    public ISearchResults<ICustomer> listCustomers(ICustomerSearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.Customer> specification = new Specification<com.sitewhere.rdb.entities.Customer>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.Customer> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if ((criteria.getRootOnly() != null) && (criteria.getRootOnly().booleanValue() == true)) {
                    Path path = root.get("parentId");
                    predicates.add(cb.isNull(path));
                } else if (criteria.getParentCustomerId() != null) {
                    Path path = root.get("parentId");
                    predicates.add(cb.equal(path, criteria.getParentCustomerId()));
                }
                if (criteria.getCustomerTypeId() != null) {
                    Path path = root.get("customerTypeId");
                    predicates.add(cb.equal(path, criteria.getCustomerTypeId()));
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };
        List<com.sitewhere.rdb.entities.Customer> list = dbClient.getDbManager().getCustomerRepository().findAllOrderByName(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public List<? extends ITreeNode> getCustomersTree() throws SiteWhereException {
        ISearchResults<ICustomer> all = listCustomers(new CustomerSearchCriteria(1, 0));
        return TreeBuilder.buildTree(all.getResults());
    }

    @Override
    public ICustomer deleteCustomer(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Customer> opt = dbClient.getDbManager().getCustomerRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getCustomerRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public IAreaType createAreaType(IAreaTypeCreateRequest request) throws SiteWhereException {
        // Convert contained area type tokens to ids.
        List<UUID> catids = convertAreaTypeTokensToIds(request.getContainedAreaTypeTokens());

        // Use common logic so all backend implementations work the same.
        AreaType type = DeviceManagementPersistence.areaTypeCreateLogic(request, catids);
        com.sitewhere.rdb.entities.AreaType created = new com.sitewhere.rdb.entities.AreaType();
        BeanUtils.copyProperties(type, created);
        return created;
    }

    @Override
    public IAreaType getAreaType(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.AreaType> opt = dbClient.getDbManager().getAreaTypeRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IAreaType getAreaTypeByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.AreaType> opt = dbClient.getDbManager().getAreaTypeRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IAreaType updateAreaType(UUID id, IAreaTypeCreateRequest request) throws SiteWhereException {
        return null;
    }

    @Override
    public ISearchResults<IAreaType> listAreaTypes(ISearchCriteria criteria) throws SiteWhereException {
        List<com.sitewhere.rdb.entities.AreaType> list = dbClient.getDbManager().getAreaTypeRepository().findAllOrderByName();
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IAreaType deleteAreaType(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.AreaType> opt = dbClient.getDbManager().getAreaTypeRepository().findById(id);
        if(!opt.isPresent()) {
            throw new SiteWhereSystemException(ErrorCode.InvalidAreaTypeToken, ErrorLevel.ERROR);
        }
        com.sitewhere.rdb.entities.AreaType deleted = opt.get();
        dbClient.getDbManager().getAreaTypeRepository().delete(deleted);
        return deleted;
    }

    @Override
    public IArea createArea(IAreaCreateRequest request) throws SiteWhereException {
        // Look up area type.
        IAreaType areaType = getAreaTypeByToken(request.getAreaTypeToken());
        if (areaType == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidAreaTypeToken, ErrorLevel.ERROR);
        }

        // Look up parent area.
        IArea parentArea = (request.getParentToken() != null) ? getAreaByToken(request.getParentToken()) : null;

        // Use common logic so all backend implementations work the same.
        Area area = DeviceManagementPersistence.areaCreateLogic(request, areaType, parentArea);
        com.sitewhere.rdb.entities.Area created = new com.sitewhere.rdb.entities.Area();
        BeanUtils.copyProperties(area, created);
        created = dbClient.getDbManager().getAreaRepository().save(created);
        return created;
    }

    @Override
    public IArea getArea(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Area> opt = dbClient.getDbManager().getAreaRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IArea getAreaByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Area> opt = dbClient.getDbManager().getAreaRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public List<IArea> getAreaChildren(String token) throws SiteWhereException {
        IArea existing = getAreaByToken(token);
        if (existing == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidAreaToken, ErrorLevel.ERROR);
        }
        List<com.sitewhere.rdb.entities.Area> list = dbClient.getDbManager().getAreaRepository().findAllByParentIdOrderByName(existing.getId());
        List<IArea> newList = new ArrayList<>();
        for(com.sitewhere.rdb.entities.Area a : list) {
            newList.add(a);
        }
        return newList;
    }

    @Override
    public IArea updateArea(UUID id, IAreaCreateRequest request) throws SiteWhereException {
        com.sitewhere.rdb.entities.Area area = null;
        Optional<com.sitewhere.rdb.entities.Area> opt = dbClient.getDbManager().getAreaRepository().findById(id);
        if(opt.isPresent()) {
            area = opt.get();

            Area target = new Area();
            // Use common update logic.
            DeviceManagementPersistence.areaUpdateLogic(request, target);
            target.setId(area.getId());
            BeanUtils.copyProperties(target, area);

            area = dbClient.getDbManager().getAreaRepository().save(area);
        }
        return area;
    }

    @Override
    public ISearchResults<IArea> listAreas(IAreaSearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.Area> specification = new Specification<com.sitewhere.rdb.entities.Area>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.Area> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if ((criteria.getRootOnly() != null) && (criteria.getRootOnly().booleanValue() == true)) {
                    Path path = root.get("parentId");
                    predicates.add(cb.isNull(path));
                } else if (criteria.getParentAreaToken() != null) {
                    try {
                        Optional<com.sitewhere.rdb.entities.Area> opt = dbClient.getDbManager().getAreaRepository().findByToken(criteria.getParentAreaToken());
                        com.sitewhere.rdb.entities.Area parent = opt.get();
                        Path path = root.get("parentId");
                        predicates.add(cb.equal(path, parent.getId()));
                    } catch (SiteWhereException e) {
                        e.printStackTrace();
                    }
                }
                if (criteria.getAreaTypeToken() != null) {
                    try {
                        Optional<com.sitewhere.rdb.entities.AreaType> opt = dbClient.getDbManager().getAreaTypeRepository().findByToken(criteria.getAreaTypeToken());
                        com.sitewhere.rdb.entities.AreaType type = opt.get();
                        Path path = root.get("areaTypeId");
                        predicates.add(cb.equal(path, type.getId()));
                    } catch (SiteWhereException e) {
                        e.printStackTrace();
                    }
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };
        List<com.sitewhere.rdb.entities.Area> list = dbClient.getDbManager().getAreaRepository().findAllOrderByName(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public List<? extends ITreeNode> getAreasTree() throws SiteWhereException {
        ISearchResults<IArea> all = listAreas(new AreaSearchCriteria(1, 0));
        return TreeBuilder.buildTree(all.getResults());
    }

    @Override
    public IArea deleteArea(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Area> opt = dbClient.getDbManager().getAreaRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getAreaRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public IZone createZone(IZoneCreateRequest request) throws SiteWhereException {
        if (request.getAreaToken() == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidAreaToken, ErrorLevel.ERROR);
        }

        IArea area = null;
        if (request.getAreaToken() != null) {
            area = getAreaByToken(request.getAreaToken());
            if (area == null) {
                throw new SiteWhereSystemException(ErrorCode.InvalidAreaToken, ErrorLevel.ERROR);
            }
        }

        Zone zone = DeviceManagementPersistence.zoneCreateLogic(request, area, UUID.randomUUID().toString());
        com.sitewhere.rdb.entities.Zone created = new com.sitewhere.rdb.entities.Zone();
        BeanUtils.copyProperties(zone, created);
        created = dbClient.getDbManager().getZoneRepository().save(created);
        return created;
    }

    @Override
    public IZone getZone(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Zone> opt = dbClient.getDbManager().getZoneRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IZone getZoneByToken(String zoneToken) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Zone> opt = dbClient.getDbManager().getZoneRepository().findByToken(zoneToken);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IZone updateZone(UUID id, IZoneCreateRequest request) throws SiteWhereException {
        return null;
    }

    @Override
    public ISearchResults<IZone> listZones(IZoneSearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.Zone> specification = new Specification<com.sitewhere.rdb.entities.Zone>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.Zone> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (criteria.getAreaId() != null) {
                    Path path = root.get("areaId");
                    predicates.add(cb.equal(path, criteria.getAreaId()));
                }
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };
        List<com.sitewhere.rdb.entities.Zone> list = dbClient.getDbManager().getZoneRepository().findAllOrderByCreatedDateDesc(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IZone deleteZone(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.Zone> opt = dbClient.getDbManager().getZoneRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getZoneRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public IDeviceGroup createDeviceGroup(IDeviceGroupCreateRequest request) throws SiteWhereException {
        // Use common logic so all backend implementations work the same.
        DeviceGroup group = DeviceManagementPersistence.deviceGroupCreateLogic(request);
        com.sitewhere.rdb.entities.DeviceGroup created = new com.sitewhere.rdb.entities.DeviceGroup();
        BeanUtils.copyProperties(group, created);
        created = dbClient.getDbManager().getDeviceGroupRepository().save(created);
        return created;
    }

    @Override
    public IDeviceGroup getDeviceGroup(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceGroup> opt = dbClient.getDbManager().getDeviceGroupRepository().findById(id);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceGroup getDeviceGroupByToken(String token) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceGroup> opt = dbClient.getDbManager().getDeviceGroupRepository().findByToken(token);
        if(opt.isPresent()) {
            return opt.get();
        }
        return null;
    }

    @Override
    public IDeviceGroup updateDeviceGroup(UUID id, IDeviceGroupCreateRequest request) throws SiteWhereException {
        return null;
    }

    @Override
    public ISearchResults<IDeviceGroup> listDeviceGroups(ISearchCriteria criteria) throws SiteWhereException {
        List<com.sitewhere.rdb.entities.DeviceGroup> list = dbClient.getDbManager().getDeviceGroupRepository().findAllOrderByCreatedDateDesc();
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public ISearchResults<IDeviceGroup> listDeviceGroupsWithRole(String role, ISearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.DeviceGroup> specification = new Specification<com.sitewhere.rdb.entities.DeviceGroup>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.DeviceGroup> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                Path path = root.get("roles");
                predicates.add(cb.in(path));
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };

        List<com.sitewhere.rdb.entities.DeviceGroup> list = dbClient.getDbManager().getDeviceGroupRepository().findAllOrderByCreatedDateDesc(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    @Override
    public IDeviceGroup deleteDeviceGroup(UUID id) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceGroup> opt = dbClient.getDbManager().getDeviceGroupRepository().findById(id);
        if(opt.isPresent()) {
            dbClient.getDbManager().getDeviceGroupRepository().deleteById(id);
        }
        return opt.get();
    }

    @Override
    public List<IDeviceGroupElement> addDeviceGroupElements(UUID groupId, List<IDeviceGroupElementCreateRequest> elements, boolean ignoreDuplicates) throws SiteWhereException {
        Optional<com.sitewhere.rdb.entities.DeviceGroupElement> opt = dbClient.getDbManager().getDeviceGroupElementRepository().findById(groupId);
        List<IDeviceGroupElement> results = new ArrayList<IDeviceGroupElement>();
        com.sitewhere.rdb.entities.DeviceGroupElement added = opt.get();
        for (IDeviceGroupElementCreateRequest request : elements) {
            // Look up referenced device if provided.
            IDevice device = null;
            if (request.getDeviceToken() != null) {
                device = getDeviceByToken(request.getDeviceToken());
                if (device == null) {
                    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceToken, ErrorLevel.ERROR);
                }
            }
            // Look up referenced nested group if provided.
            IDeviceGroup nested = null;
            if (request.getNestedGroupToken() != null) {
                nested = getDeviceGroupByToken(request.getNestedGroupToken());
                if (nested == null) {
                    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceGroupToken, ErrorLevel.ERROR);
                }
            }
            DeviceGroup group = new DeviceGroup();
            group.setId(added.getId());
            DeviceGroupElement element = DeviceManagementPersistence.deviceGroupElementCreateLogic(request, group, device, nested);
            BeanUtils.copyProperties(element, added);
            added = dbClient.getDbManager().getDeviceGroupElementRepository().save(added);
            results.add(added);
        }
        return results;
    }

    @Override
    public List<IDeviceGroupElement> removeDeviceGroupElements(List<UUID> elementIds) throws SiteWhereException {
        List<IDeviceGroupElement> deleted = new ArrayList<IDeviceGroupElement>();
        for (UUID elementId : elementIds) {
            List<com.sitewhere.rdb.entities.DeviceGroupElement> list = dbClient.getDbManager().getDeviceGroupElementRepository().findAllById(elementId);
            for (com.sitewhere.rdb.entities.DeviceGroupElement ele : list) {
                dbClient.getDbManager().getDeviceGroupElementRepository().deleteById(ele.getId());
                deleted.add(ele);
            }
        }
        return deleted;
    }

    @Override
    public ISearchResults<IDeviceGroupElement> listDeviceGroupElements(UUID groupId, ISearchCriteria criteria) throws SiteWhereException {
        Specification<com.sitewhere.rdb.entities.DeviceGroupElement> specification = new Specification<com.sitewhere.rdb.entities.DeviceGroupElement>() {
            @Override
            public Predicate toPredicate(Root<com.sitewhere.rdb.entities.DeviceGroupElement> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                Path path = root.get("groupId");
                predicates.add(cb.equal(path, groupId));
                return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
            }
        };
        List<com.sitewhere.rdb.entities.DeviceGroupElement> list = dbClient.getDbManager().getDeviceGroupElementRepository().findList(specification);
        return new ISearchResults() {

            @Override
            public long getNumResults() {
                return list.size();
            }

            @Override
            public List getResults() {
                return list;
            }
        };
    }

    /**
     * Get API device object by unique id.
     *
     * @param id
     * @return
     * @throws SiteWhereException
     */
    protected IDevice getApiDeviceById(UUID id) throws SiteWhereException {
        IDevice device = getDevice(id);
        if (device == null) {
            throw new SiteWhereSystemException(ErrorCode.InvalidDeviceId, ErrorLevel.ERROR);
        }
        return device;
    }

    /**
     * Get asset management implementation from microservice.
     *
     * @return
     */
    public IAssetManagement getAssetManagement() {
        return ((DeviceManagementMicroservice) getTenantEngine().getMicroservice()).getAssetManagementApiChannel();
    }

    /**
     * Look up a list of asset tokens to get the corresponding list of asset ids.
     *
     * @param tokens
     * @return
     * @throws SiteWhereException
     */
    protected List<UUID> getAssetIds(List<String> tokens) throws SiteWhereException {
        List<UUID> result = new ArrayList<>();
        for (String token : tokens) {
            IAsset asset = getAssetManagement().getAssetByToken(token);
            result.add(asset.getId());
        }
        return result;
    }

    /**
     * Convert a list of area type tokens to ids.
     *
     * @param tokens
     * @return
     * @throws SiteWhereException
     */
    protected List<UUID> convertCustomerTypeTokensToIds(List<String> tokens) throws SiteWhereException {
        List<UUID> cctids = new ArrayList<>();
        if (tokens != null) {
            for (String token : tokens) {
                ICustomerType contained = getCustomerTypeByToken(token);
                if (contained != null) {
                    cctids.add(contained.getId());
                }
            }
        }
        return cctids;
    }

    /**
     * Convert a list of area type tokens to ids.
     *
     * @param tokens
     * @return
     * @throws SiteWhereException
     */
    protected List<UUID> convertAreaTypeTokensToIds(List<String> tokens) throws SiteWhereException {
        List<UUID> catids = new ArrayList<>();
        if (tokens != null) {
            for (String token : tokens) {
                IAreaType contained = getAreaTypeByToken(token);
                if (contained != null) {
                    catids.add(contained.getId());
                }
            }
        }
        return catids;
    }

}